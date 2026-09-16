//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import Security
import CryptoKit

/// Internal helpers for the long-lived auth cert. Generation goes through
/// CryptoKit's `SecureEnclave.P256.Signing.PrivateKey` (hardware-backed)
/// and the in-repo `X509SelfSignedCertBuilder`, keeping the sample free of
/// the swift-certificates package dependency.
extension CertificateManager {

    /// Generates a fresh SE-backed signing key, builds a 25-year self-signed
    /// cert around it, stores both in the keychain, and returns the key.
    func generateAuthKeyAndCert(alias: String) throws -> SecureEnclave.P256.Signing.PrivateKey {
        // 1. Generate SE-backed key. The access control requires the device
        //    to be unlocked at least once since boot and binds the key to
        //    this device only — matches the previous `SecKey` configuration.
        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            [.privateKeyUsage],
            nil
        ) else {
            throw CertificateError.keyGenerationFailed(underlying: nil)
        }
        let seKey: SecureEnclave.P256.Signing.PrivateKey
        do {
            seKey = try SecureEnclave.P256.Signing.PrivateKey(accessControl: access)
        } catch {
            throw CertificateError.keyGenerationFailed(underlying: error)
        }

        // 2. Build & sign the cert via the in-repo X509 builder.
        let certDER = try buildSelfSignedAuthCertificate(seKey: seKey)

        // 3. Persist: SE blob first, then cert. If the second write fails,
        //    the blob is left around but the cert is missing — `ensureKeyPair`
        //    will detect that on next launch and wipe both.
        try writeKeychainData(seKey.dataRepresentation, account: getSEKeyBlobTag(alias: alias))
        try writeKeychainData(certDER, account: getCertificateTag(alias: alias))

        return seKey
    }

    /// Builds a 25-year self-signed P-256 X.509 cert via the in-repo
    /// `X509SelfSignedCertBuilder`. The signing key is SE-backed; the builder
    /// calls into its `signature(for:)` to produce the cert's ECDSA-SHA256
    /// signature.
    private func buildSelfSignedAuthCertificate(
        seKey: SecureEnclave.P256.Signing.PrivateKey
    ) throws -> Data {
        let notBefore = Date()
        let notAfter = Calendar.current.date(byAdding: .year, value: certValidityYears, to: notBefore)!

        return try X509SelfSignedCertBuilder.build(
            subject: "CN=Azure Vision Liveness, O=Microsoft",
            publicKeyX963: seKey.publicKey.x963Representation,
            notBefore: notBefore,
            notAfter: notAfter,
            sign: { tbs in try seKey.signature(for: tbs).derRepresentation }
        )
    }
}
