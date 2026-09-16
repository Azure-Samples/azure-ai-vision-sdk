//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

// MARK: - DeviceAttestationEndpoints

/// Host-relative paths for the auth-flow endpoints, resolved against
/// `https://<livenessHost>/`.
///
/// Defaults match the reference backend; override any subset via
/// `DeviceAttestation.initialize(...)` when a deployment uses a different
/// routing scheme (e.g. a different API prefix or versioned paths). Paths must
/// not begin with a leading `/`.
///
/// iOS counterpart of Android's `DeviceAttestationEndpoints` data class.
public struct DeviceAttestationEndpoints {
    public var attestationChallenge: String
    public var attestationVerify: String
    public var attestationRegister: String
    public var sessionToken: String
    public var livenessDigest: String

    public init(
        attestationChallenge: String = "api/attestation/challenge",
        attestationVerify: String = "api/attestation/verify",
        attestationRegister: String = "api/attestation/register",
        sessionToken: String = "api/session/token",
        livenessDigest: String = "api/liveness/digest"
    ) {
        self.attestationChallenge = attestationChallenge
        self.attestationVerify = attestationVerify
        self.attestationRegister = attestationRegister
        self.sessionToken = sessionToken
        self.livenessDigest = livenessDigest
    }
}

// MARK: - DeviceAttestation (factory)

/// Public entry point for the cert-based QuickLink auth flow — the iOS
/// counterpart of Android's `DeviceAttestation` object.
///
///   - `initialize`     — configure host + endpoints (call once before a session)
///   - `startSession`   — run the full attest flow and return an `AttestationSession`
///                        that privately owns the challengeHash + session keys
///   - `currentSession` — the single active `AttestationSession`, if any
///
/// Per-session token exchange and liveness-digest submission live on
/// `AttestationSession`; app-static config lives here.
///
/// Modeled as an `actor` so the single active-session reference and the config
/// stay consistent across the concurrent tasks that drive the URL handler and
/// the result screen (the iOS analogue of Android's `@Volatile activeSession`).
public actor DeviceAttestation {

    public static let shared = DeviceAttestation()
    private init() {}

    // MARK: Result types

    /// Result of `startSession`. Mirrors Android's `StartSessionResult`.
    public enum StartSessionResult {
        /// Attestation succeeded; `session` is ready for token + digest calls.
        case success(AttestationSession)
        case error(code: Int, message: String)
        case exception(Error)
    }

    // MARK: State

    // Only one attestation session is active at a time. Starting a new one
    // replaces (and cleans up) any previous session. Held so a later screen
    // (e.g. the result screen) can re-resolve it via `currentSession()` without
    // holding the crypto objects itself.
    private var activeSession: AttestationSession?

    // Config, installed by `initialize`. The host is required before any
    // endpoint URL can be resolved; the library does not read it from the
    // app's Info.plist itself, keeping this type free of app/build coupling.
    private var configuredHost: String?
    private var endpoints = DeviceAttestationEndpoints()

    // MARK: Configuration

    /// Configures the library. Call once (typically at the start of the flow)
    /// before `startSession`. The host is supplied by the app — keeping this
    /// type free of build-time coupling — and must be set before any endpoint
    /// URL is resolved.
    ///
    /// Unlike Android there is no `context` or `cloudProjectNumber`: iOS App
    /// Attest needs neither.
    public func initialize(
        livenessHost: String,
        endpoints: DeviceAttestationEndpoints = DeviceAttestationEndpoints()
    ) {
        self.configuredHost = livenessHost
        self.endpoints = endpoints
    }

    // MARK: Sessions

    /// Runs the full attestation flow and, on success, returns a per-session
    /// `AttestationSession`:
    ///   1. fetch challenge hash from /attestation/challenge
    ///   2. ensure a local Secure-Enclave-backed auth cert exists
    ///   3. ask the server if it already knows our cert (/attestation/verify)
    ///   4. if not, App Attest + register the cert (/attestation/register)
    ///   5. hand the server encryption pubkey + ephemeral privkey to the
    ///      returned session, which keeps them (and the challengeHash) private
    ///
    /// On success the session becomes the single active session (re-resolve it
    /// via `currentSession()`). Its ephemeral key is dropped automatically after
    /// `AttestationSession.submitLivenessDigest`, and starting another session
    /// clears this one — so callers never need to release it explicitly.
    public func startSession(
        sessionId: String,
        clientId: String,
        deviceUUID: String
    ) async -> StartSessionResult {
        guard !sessionId.isEmpty else {
            return .error(code: -1, message: "sessionId is empty")
        }
        // Session id and client id are UUIDs (the Universal Link `s` value and
        // the device's identifierForVendor). Reject anything else up front:
        // both are spliced into the endpoint query strings, and the `s` value in
        // particular arrives already percent-decoded from the inbound link, so a
        // non-UUID value could otherwise smuggle extra query parameters
        // downstream. `UUID(uuidString:)` enforces the exact 8-4-4-4-12 format
        // the backend also requires.
        guard UUID(uuidString: sessionId) != nil else {
            return .error(code: -1, message: "sessionId is not a valid session id")
        }
        guard UUID(uuidString: clientId) != nil else {
            return .error(code: -1, message: "clientId is not a valid client id")
        }

        do {
            let challengeHash = try await AttestationFlow.fetchChallenge(sParam: sessionId, clientId: clientId)

            // A prior registration is only reusable when BOTH halves survive:
            // the Secure-Enclave auth cert (Keychain) AND an attested App Attest
            // key (UserDefaults). iOS wipes UserDefaults on an app
            // uninstall/reinstall but keeps the Keychain, so after a reinstall
            // the two can disagree. Treat any such half-state as "not
            // registered" and delete the surviving half so the registration path
            // rebuilds from a clean slate — otherwise a stale auth cert would keep
            // steering us down the verify path with no attested key to prove
            // ownership, stranding the device forever.
            let authCertExists = try await CertificateManager.shared.hasCertificate(deviceUUID: deviceUUID)
            let keyExisted = authCertExists && AppAttestManager.shared.hasAttestedKey()
            if !keyExisted {
                AppAttestManager.shared.resetCachedKey()
                try await CertificateManager.shared.deleteCertificate(deviceUUID: deviceUUID)
            }
            _ = try await CertificateManager.shared.ensureKeyPair(deviceUUID: deviceUUID)

            let (serverKeyPem, ephemeralKey) = try await AttestationFlow.obtainSessionKeys(
                sParam: sessionId,
                clientId: clientId,
                system: AttestationSession.system,
                deviceUUID: deviceUUID,
                challengeHash: challengeHash,
                keyExisted: keyExisted
            )

            let serverKey = try CryptoHelper.parsePublicKey(from: serverKeyPem)
            let session = AttestationSession(
                sessionId: sessionId,
                clientId: clientId,
                deviceUUID: deviceUUID,
                challengeHash: challengeHash,
                serverEncryptionPublicKey: serverKey,
                ephemeralEncryptionPrivateKey: ephemeralKey
            )

            // Only one active session: replace (and clean) any previous one so
            // its ephemeral key doesn't linger if that session was abandoned.
            await activeSession?.cleanup()
            activeSession = session
            return .success(session)
        } catch let DeviceAttestationError.networkError(code, message) {
            return .error(code: code, message: message)
        } catch let DeviceAttestationError.configurationError(message) {
            return .error(code: -1, message: message)
        } catch {
            return .exception(error)
        }
    }

    /// The single active session, or nil if none has been started / it was
    /// cleaned up.
    public func currentSession() -> AttestationSession? {
        return activeSession
    }

    /// Clears the active session. Called by `AttestationSession.cleanup`.
    func clearSession(_ session: AttestationSession) {
        // Guard against a newer session having already replaced this one.
        if activeSession === session {
            activeSession = nil
        }
    }

    // MARK: Endpoint resolution (internal)

    /// Absolute URL for a host-relative `path`, resolved as
    /// `https://<livenessHost>/<path>`. Requires `initialize(livenessHost:)`
    /// to have been called first.
    private func endpointUrl(_ path: String) throws -> String {
        guard let host = configuredHost else {
            throw DeviceAttestationError.configurationError(
                "DeviceAttestation.initialize(livenessHost:) must be called before resolving endpoint URLs"
            )
        }
        return "https://\(host)/\(path)"
    }

    var attestationChallengeUrl: String {
        get throws { try endpointUrl(endpoints.attestationChallenge) }
    }
    var attestationVerifyUrl: String {
        get throws { try endpointUrl(endpoints.attestationVerify) }
    }
    var attestationRegisterUrl: String {
        get throws { try endpointUrl(endpoints.attestationRegister) }
    }
    var sessionTokenUrl: String {
        get throws { try endpointUrl(endpoints.sessionToken) }
    }
    var livenessDigestUrl: String {
        get throws { try endpointUrl(endpoints.livenessDigest) }
    }
}

// MARK: - AttestationFlow (internal glue)

/// Attestation half of the QuickLink flow, factored out of `DeviceAttestation`
/// so each step stays small enough to review on its own. Mirrors Android's
/// `AttestationFlow` object.
///
///   1. fetch the server challenge          → `fetchChallenge`   (/attestation/challenge)
///   2. obtain server-side session keys      → `obtainSessionKeys`
///        a. ask whether server knows cert   → /attestation/verify
///        b. if not, attest + register       → `registerNewAttestation` (/attestation/register)
///
/// Kept UI-free (no progress-overlay coupling); the caller owns any progress
/// reporting. Server HTTP failures surface as `DeviceAttestationError.networkError`,
/// which `DeviceAttestation.startSession` maps to `StartSessionResult.error`.
enum AttestationFlow {

    /// POST /attestation/challenge and return the challenge hash.
    static func fetchChallenge(sParam: String, clientId: String) async throws -> String {
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            fetchAttestationChallenge(sParam: sParam, clientId: clientId) { result in
                continuation.resume(with: result)
            }
        }
    }

    /// Returns `(serverEncryptionPublicKeyPEM, ephemeralEncryptionPrivateKey)`:
    ///   - from /attestation/verify if the server already has our cert, OR
    ///   - from /attestation/register after a fresh App Attest registration.
    static func obtainSessionKeys(
        sParam: String,
        clientId: String,
        system: String,
        deviceUUID: String,
        challengeHash: String,
        keyExisted: Bool
    ) async throws -> (String, P256.KeyAgreement.PrivateKey) {
        // Skip the verify round trip unless a prior registration is fully
        // present. `keyExisted` (set by startSession) is true only when both the
        // auth cert AND an attested App Attest key exist, so we can prove
        // ownership of whatever the server has on file. When it's false there is
        // nothing to verify — fall straight through to registration.
        let certCheckResult: AttestationVerifyResult
        if keyExisted {
            certCheckResult = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<AttestationVerifyResult, Error>) in
                verifyAttestation(
                    sParam: sParam,
                    clientId: clientId,
                    system: system,
                    challengeHash: challengeHash,
                    deviceUUID: deviceUUID
                ) { result in
                    continuation.resume(with: result)
                }
            }
        } else {
            certCheckResult = AttestationVerifyResult(
                certExists: false,
                serverEncryptionPublicKey: nil,
                ephemeralEncryptionPrivateKey: nil
            )
        }

        if certCheckResult.certExists {
            guard let serverKey = certCheckResult.serverEncryptionPublicKey else {
                throw DeviceAttestationError.cryptoError("Server encryption public key not returned for existing certificate")
            }
            guard let ephemeralKey = certCheckResult.ephemeralEncryptionPrivateKey else {
                throw DeviceAttestationError.cryptoError("Ephemeral encryption private key not returned for existing certificate")
            }
            return (serverKey, ephemeralKey)
        }

        // Registration path.
        let registerResult = try await registerNewAttestation(
            sParam: sParam,
            clientId: clientId,
            system: system,
            deviceUUID: deviceUUID,
            challengeHash: challengeHash
        )
        return (registerResult.serverEncryptionPublicKey, registerResult.ephemeralEncryptionPrivateKey)
    }

    /// First-time cert registration: rotates the App Attest key if needed,
    /// generates `attestKey` + `generateAssertion` proofs, and calls
    /// /attestation/register.
    private static func registerNewAttestation(
        sParam: String,
        clientId: String,
        system: String,
        deviceUUID: String,
        challengeHash: String
    ) async throws -> AttestationRegisterResult {
        // App Attest support gate.
        guard AppAttestManager.shared.isSupported() else {
            throw DeviceAttestationError.attestationError(DeviceAttestationError.notSupported)
        }

        // Apple's DCAppAttestService.attestKey is one-shot per keyId. If a prior
        // run already attested the cached keyId (isAttestationComplete() == true)
        // but we landed back in the registration branch — typically because the
        // server's cert record TTL'd out — reusing that keyId can fail with
        // DCError.invalidInput. Rotate the App Attest key here so the upcoming
        // attestKey call is the first one against a fresh keyId. The Secure-
        // Enclave-backed auth cert is independent and remains; only the App
        // Attest key changes, which is fine because the server reverifies
        // provenance through whichever credCert the new attestation carries.
        if AppAttestManager.shared.isAttestationComplete() {
            AppAttestManager.shared.resetCachedKey()
        }

        let keyId = try await AppAttestManager.shared.getOrGenerateKeyAsync()

        // Two separate App Attest proofs ride in attestJson, both verified
        // server-side:
        //   1. attestKey         bound to the server CHALLENGE only.
        //   2. generateAssertion bound to the auth cert THUMBPRINT only.
        // Both inputs are already 32-byte SHA-256 digests hex-encoded for
        // transport; decode to raw bytes and pass to App Attest directly —
        // re-hashing would just produce another 32-byte digest with the same
        // entropy.
        let challengeOnlyData = try hexStringToBytes(challengeHash)
        let attestation = try await AppAttestManager.shared.attestKeyAsync(
            keyId: keyId,
            challengeHash: challengeOnlyData
        )
        AppAttestManager.shared.markAttestationComplete()

        let authThumbprint = try await CertificateManager.shared.getCertificateThumbprint(deviceUUID: deviceUUID)
        let thumbprintOnlyData = try hexStringToBytes(authThumbprint)
        let assertion = try await AppAttestManager.shared.generateAssertionAsync(
            keyId: keyId,
            clientDataHash: thumbprintOnlyData
        )

        let attestationToken = attestation.base64EncodedString()
        let assertionToken = assertion.base64EncodedString()

        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<AttestationRegisterResult, Error>) in
            registerAttestation(
                sParam: sParam,
                clientId: clientId,
                system: system,
                challengeHash: challengeHash,
                deviceUUID: deviceUUID,
                attestationToken: attestationToken,
                assertionToken: assertionToken
            ) { result in
                continuation.resume(with: result)
            }
        }
    }
}
