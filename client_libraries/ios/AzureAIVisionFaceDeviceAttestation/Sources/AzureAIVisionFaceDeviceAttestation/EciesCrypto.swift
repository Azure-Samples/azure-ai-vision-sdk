//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

/**
 * ECIES-AEAD-HKDF on P-256 — the Tink-format scheme used for encrypted
 * payloads. CryptoKit handles everything:
 *
 *   1. Ephemeral ECDH key agreement (P256.KeyAgreement)
 *   2. HKDF-SHA256 key derivation   (HKDF<SHA256>)
 *   3. AES-256-GCM authenticated enc (AES.GCM)
 *
 * Wire format (matches the server's Python Tink and Android's hand-rolled
 * version byte-for-byte):
 *
 *   point(65) || iv(12) || ciphertext || authTag(16)
 */
enum EciesCrypto {

    /// Encrypts [data] for [recipientPublicKey]. Returns the Tink wire bytes.
    static func encrypt(_ data: Data, publicKey recipientPublicKey: P256.KeyAgreement.PublicKey) throws -> Data {
        // 1. Ephemeral P-256 keypair, lives only on this stack frame.
        let ephemeralPrivateKey = P256.KeyAgreement.PrivateKey()
        let ephemeralPublicKey = ephemeralPrivateKey.publicKey

        // 2. ECDH(ephemeral_priv, recipient_pub) → shared secret.
        let sharedSecret = try ephemeralPrivateKey.sharedSecretFromKeyAgreement(with: recipientPublicKey)

        // 3. AES-256 key = HKDF(SHA-256, ikm = ephemeral_point || shared_secret).
        //    Shoup-style: empty salt, empty info. Must match Tink (server) and
        //    Android (`Hkdf.computeHkdf`); changing the IKM construction here
        //    would silently produce a different key and the server would log
        //    "Decryption failed".
        let aesKey = deriveTinkAESKey(
            ephemeralPoint: ephemeralPublicKey.x963Representation,
            sharedSecret: sharedSecret.withUnsafeBytes { Data($0) }
        )

        // 4. AES-256-GCM seal. `sealedBox.combined` returns iv(12) || ct || tag(16).
        let sealedBox = try AES.GCM.seal(data, using: aesKey)
        guard let combined = sealedBox.combined else {
            throw CryptoHelper.CryptoError.encryptionFailed("AES.GCM did not produce combined output")
        }

        // 5. Tink wire layout: point(65) || iv(12) || ct || tag(16).
        return ephemeralPublicKey.x963Representation + combined
    }

    /// Decrypts Tink wire bytes back to plaintext.
    static func decrypt(_ ciphertext: Data, privateKey: P256.KeyAgreement.PrivateKey) throws -> Data {
        guard ciphertext.count >= 65 + 12 + 16 else {
            throw CryptoHelper.CryptoError.invalidFormat
        }

        let pointBytes = ciphertext.prefix(65)
        let aeadCiphertext = ciphertext.suffix(from: ciphertext.startIndex + 65)

        let ephemeralPublicKey = try P256.KeyAgreement.PublicKey(x963Representation: pointBytes)
        let sharedSecret = try privateKey.sharedSecretFromKeyAgreement(with: ephemeralPublicKey)

        let aesKey = deriveTinkAESKey(
            ephemeralPoint: ephemeralPublicKey.x963Representation,
            sharedSecret: sharedSecret.withUnsafeBytes { Data($0) }
        )

        let sealedBox = try AES.GCM.SealedBox(combined: aeadCiphertext)
        return try AES.GCM.open(sealedBox, using: aesKey)
    }

    /// HKDF-SHA256 with `ikm = ephemeralPoint || sharedSecret`, empty salt
    /// and info — Tink's ECIES-AEAD-HKDF parameters. Output is 32 bytes
    /// (AES-256).
    private static func deriveTinkAESKey(ephemeralPoint: Data, sharedSecret: Data) -> SymmetricKey {
        return HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: ephemeralPoint + sharedSecret),
            salt: Data(),
            info: Data(),
            outputByteCount: 32
        )
    }
}
