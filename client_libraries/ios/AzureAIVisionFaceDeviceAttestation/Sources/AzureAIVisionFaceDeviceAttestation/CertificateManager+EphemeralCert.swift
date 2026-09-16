//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

/// Per-session ephemeral encryption certs.
///
/// Lifecycle: generated at the start of an auth flow, returned to the caller
/// as `(certPEM, keyAgreementPrivateKey)`. The PEM is handed to the server;
/// the private key is owned by the `AttestationSession` for the session so we
/// can decrypt server responses, and is dropped when the session ends.
///
/// These keys are NOT in the Secure Enclave — that lets us tear them down
/// cleanly when the session is over and keeps the type as a plain
/// `P256.KeyAgreement.PrivateKey` (no Secure-Enclave wrapper variant needed).
extension CertificateManager {

    /// Bundle returned from [generateEphemeralEncryptionCert].
    struct EphemeralEncryptionCert {
        let certificatePEM: String
        let privateKey: P256.KeyAgreement.PrivateKey
    }

    /**
     * Generates a fresh P-256 keypair and a 1-day self-signed cert.
     *
     * One key, two uses:
     *   - cert is signed with `P256.Signing.PrivateKey` (this is what
     *     `X509SelfSignedCertBuilder` signs with)
     *   - the SAME 32-byte scalar is re-wrapped as a
     *     `P256.KeyAgreement.PrivateKey` and returned to the caller for
     *     ECDH-decrypt of server responses
     *
     * Because both wrappers share the underlying scalar, the cert's embedded
     * public key matches the key-agreement public key, so the server's
     * ECIES-encrypt to the cert's pubkey decrypts cleanly with the returned
     * key-agreement key.
     *
     * Nothing is persisted to the keychain; the caller is expected to keep
     * the returned key in memory (typically via the `AttestationSession`) for
     * the session and let it deallocate when the session ends.
     */
    func generateEphemeralEncryptionCert() throws -> EphemeralEncryptionCert {
        // 1. Software P-256 signing key — used to sign the cert.
        let signingKey = P256.Signing.PrivateKey()

        // 2. Same scalar, key-agreement view — used by the caller for ECDH.
        let keyAgreementKey = try P256.KeyAgreement.PrivateKey(rawRepresentation: signingKey.rawRepresentation)

        // 3. Build & sign 1-day cert via the in-repo X509 builder.
        let notBefore = Date()
        let notAfter = Calendar.current.date(byAdding: .day, value: 1, to: notBefore)!

        let certDER = try X509SelfSignedCertBuilder.build(
            subject: "CN=Ephemeral Encryption, O=Azure Vision Liveness",
            publicKeyX963: signingKey.publicKey.x963Representation,
            notBefore: notBefore,
            notAfter: notAfter,
            sign: { tbs in try signingKey.signature(for: tbs).derRepresentation }
        )
        let certPEM = derToPEMCertificate(certDER)

        return EphemeralEncryptionCert(certificatePEM: certPEM, privateKey: keyAgreementKey)
    }

    /// No-op kept for source-compat with existing callers in the *Api.swift files.
    ///
    /// Ephemeral keys are now in-memory only; there's nothing to delete from
    /// the keychain. The CryptoKit value drops when the last reference goes
    /// out of scope (typically when the `AttestationSession` is cleaned up at
    /// session end).
    func deleteEphemeralEncryptionCert(_ privateKey: P256.KeyAgreement.PrivateKey) {
        // Intentionally empty — no keychain entry to remove.
    }
}
