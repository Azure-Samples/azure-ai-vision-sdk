//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import DeviceCheck
import CryptoKit

/// Wraps Apple's `DCAppAttestService` with caching, persistence, and async helpers.
///
/// Key concepts:
/// - **keyId**: Apple-issued identifier for a Secure-Enclave-backed App Attest key.
///   Persisted in UserDefaults so subsequent app launches reuse the same key.
/// - **attestKey**: one-shot per keyId. Used at first-time cert registration to
///   prove "this key was minted by Apple's secure hardware in response to *this*
///   server challenge."
/// - **generateAssertion**: callable many times per keyId. Used per-request to
///   prove "the App Attest key is signing over this payload right now."
class AppAttestManager {
    static let shared = AppAttestManager()
    private let service = DCAppAttestService.shared
    private let keyIdKey = "com.microsoft.azurevisionliveness.attestKeyId"
    private let attestationCompleteKey = "com.microsoft.azurevisionliveness.attestationComplete"

    func isSupported() -> Bool {
        return service.isSupported
    }

    /// Returns the cached App Attest keyId WITHOUT generating a new one. Used by
    /// the ongoing-assertion call sites (attestation/verify, session/token, liveness/digest): generating
    /// a fresh keyId there would be useless because the server's persisted
    /// credCert is tied to the originally attested keyId — a brand-new key
    /// would produce assertions that fail server-side verification. nil means no
    /// attested key exists on this install (e.g., UserDefaults cleared by an app
    /// reinstall). `startSession` gates the verify path on `hasAttestedKey()`, so
    /// this is non-nil there; at the mid-session sites a nil here is terminal.
    var cachedKeyId: String? {
        return UserDefaults.standard.string(forKey: keyIdKey)
    }

    /// Returns the cached keyId or asks Apple to mint a new one.
    func getOrGenerateKey(completion: @escaping (Result<String, Error>) -> Void) {
        if let existingKeyId = UserDefaults.standard.string(forKey: keyIdKey) {
            completion(.success(existingKeyId))
            return
        }
        service.generateKey { keyId, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let keyId = keyId else {
                completion(.failure(DeviceAttestationError.keyGenerationFailed))
                return
            }
            UserDefaults.standard.set(keyId, forKey: self.keyIdKey)
            completion(.success(keyId))
        }
    }

    func isAttestationComplete() -> Bool {
        return UserDefaults.standard.bool(forKey: attestationCompleteKey)
    }

    func markAttestationComplete() {
        UserDefaults.standard.set(true, forKey: attestationCompleteKey)
    }

    /// True when a fully attested App Attest key exists on THIS install: a
    /// keyId is cached AND its one-shot attestation completed. Both facts live
    /// in UserDefaults, which iOS clears on an app uninstall — so this is false
    /// after a reinstall even when the Keychain auth cert survives. Callers gate
    /// the verify path on this: without an attested key nothing can produce an
    /// ongoing assertion the server will accept, so they must re-register.
    func hasAttestedKey() -> Bool {
        return cachedKeyId != nil && isAttestationComplete()
    }

    /// Clears the cached App Attest keyId and the attestation-complete flag.
    /// Call this before re-entering the registration branch when a previous
    /// attestation has already happened on this device — Apple's
    /// DCAppAttestService.attestKey is documented as one-shot per keyId, so
    /// re-attesting the same key can fail with DCError.invalidInput. The
    /// Secure-Enclave-backed auth cert is independent and not touched here;
    /// only the App Attest key rotates.
    func resetCachedKey() {
        UserDefaults.standard.removeObject(forKey: keyIdKey)
        UserDefaults.standard.removeObject(forKey: attestationCompleteKey)
    }

    func attestKey(keyId: String, challengeHash: Data, completion: @escaping (Result<Data, Error>) -> Void) {
        service.attestKey(keyId, clientDataHash: challengeHash) { attestation, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let attestation = attestation else {
                completion(.failure(DeviceAttestationError.attestationFailed))
                return
            }
            completion(.success(attestation))
        }
    }

    func generateAssertion(keyId: String, challengeHash: Data, completion: @escaping (Result<Data, Error>) -> Void) {
        service.generateAssertion(keyId, clientDataHash: challengeHash) { assertion, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let assertion = assertion else {
                completion(.failure(DeviceAttestationError.assertionFailed))
                return
            }
            completion(.success(assertion))
        }
    }

    // MARK: - async/await helpers

    /// Async wrapper around `generateAssertion(keyId:challengeHash:)`.
    /// Throws `DeviceAttestationError.notSupported` if no keyId has been cached yet —
    /// the caller is responsible for handling that desynced state.
    func generateOngoingAssertionAsync(over payload: Data) async throws -> Data {
        guard let keyId = cachedKeyId else {
            throw DeviceAttestationError.notSupported
        }
        let clientDataHash = Data(SHA256.hash(data: payload))
        return try await withCheckedThrowingContinuation { continuation in
            generateAssertion(keyId: keyId, challengeHash: clientDataHash) { result in
                continuation.resume(with: result)
            }
        }
    }

    /// Async wrapper around `attestKey(keyId:challengeHash:)`.
    func attestKeyAsync(keyId: String, challengeHash: Data) async throws -> Data {
        return try await withCheckedThrowingContinuation { continuation in
            attestKey(keyId: keyId, challengeHash: challengeHash) { result in
                continuation.resume(with: result)
            }
        }
    }

    /// Async wrapper around `generateAssertion(keyId:challengeHash:)` for a
    /// caller-provided raw `clientDataHash` (e.g., the registration-time
    /// thumbprint binding, where the input is not the raw payload).
    func generateAssertionAsync(keyId: String, clientDataHash: Data) async throws -> Data {
        return try await withCheckedThrowingContinuation { continuation in
            generateAssertion(keyId: keyId, challengeHash: clientDataHash) { result in
                continuation.resume(with: result)
            }
        }
    }

    /// Async wrapper around `getOrGenerateKey`.
    func getOrGenerateKeyAsync() async throws -> String {
        return try await withCheckedThrowingContinuation { continuation in
            getOrGenerateKey { result in
                continuation.resume(with: result)
            }
        }
    }
}
