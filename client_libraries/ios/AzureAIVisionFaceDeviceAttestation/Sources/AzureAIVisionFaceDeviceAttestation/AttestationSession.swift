//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

/// A single attestation session, created by `DeviceAttestation.startSession`.
///
/// Owns the per-session secrets — the server `challengeHash`, the server
/// encryption public key, and this client's ephemeral encryption private key —
/// privately, so they never leak through the public API. Reuse the same
/// instance for the token exchange and the final liveness digest; the ephemeral
/// key is dropped automatically once `submitLivenessDigest` completes (and
/// whenever a new session replaces this one), so callers never need to release
/// it themselves.
///
/// Instances are created only by the library; callers obtain one from
/// `DeviceAttestation.startSession` or re-resolve it via
/// `DeviceAttestation.currentSession()`. iOS counterpart of Android's
/// `AttestationSession`.
///
/// An `actor` because it owns the session's encryption keys — mutable state
/// that outlives a single call — so isolation keeps that access safe.
/// `fetchSessionToken` and `submitLivenessDigest` live in extensions
/// (see SessionTokenApi.swift / LivenessDigestApi.swift).
public actor AttestationSession {

    /// Client OS tag sent to the backend (`system` / `os`).
    static let system = "ios"

    /// Server session id (the Universal Link `s` parameter).
    nonisolated let sessionId: String
    /// Correlation id sent to the server (`cid` / `clientId`).
    nonisolated let clientId: String
    /// Stable per-device id used for the local auth-cert keychain alias.
    nonisolated let deviceUUID: String

    // Per-session secrets — never exposed publicly.
    let challengeHash: String
    let serverEncryptionPublicKey: P256.KeyAgreement.PublicKey
    /// Dropped by `cleanup()`; nil once the session is finished.
    var ephemeralEncryptionPrivateKey: P256.KeyAgreement.PrivateKey?

    init(
        sessionId: String,
        clientId: String,
        deviceUUID: String,
        challengeHash: String,
        serverEncryptionPublicKey: P256.KeyAgreement.PublicKey,
        ephemeralEncryptionPrivateKey: P256.KeyAgreement.PrivateKey
    ) {
        self.sessionId = sessionId
        self.clientId = clientId
        self.deviceUUID = deviceUUID
        self.challengeHash = challengeHash
        self.serverEncryptionPublicKey = serverEncryptionPublicKey
        self.ephemeralEncryptionPrivateKey = ephemeralEncryptionPrivateKey
    }

    // MARK: Result types

    /// Result of `fetchSessionToken`. Mirrors Android's `SessionTokenResult`.
    public enum SessionTokenResult {
        case success(String)
        case error(code: Int, message: String)
        case exception(Error)
    }

    /// Result of `submitLivenessDigest`. Mirrors Android's `LivenessDigestResult`.
    public enum LivenessDigestResult {
        case success
        case error(code: Int, message: String)
        case exception(Error)
    }

    // MARK: Cleanup

    /// Drops this session's ephemeral encryption key and clears it as
    /// `DeviceAttestation`'s active session. Invoked automatically after the
    /// digest and when a new session replaces this one; idempotent.
    ///
    /// iOS ephemeral keys are in-memory CryptoKit values (not keychain-backed),
    /// so dropping the reference is all that's needed — the value deallocates
    /// once the last strong reference goes away.
    func cleanup() async {
        ephemeralEncryptionPrivateKey = nil
        await DeviceAttestation.shared.clearSession(self)
    }
}
