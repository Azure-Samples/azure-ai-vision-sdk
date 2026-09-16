//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

/**
 * Public crypto API used across the auth flow. After the SecureEnclave +
 * CryptoKit migration, all key types are CryptoKit's P256 family.
 *
 *   ECIES (encrypt/decrypt) → [EciesCrypto]
 *   PEM / base64           → [KeyEncoding]
 */
enum CryptoHelper {

    /// Errors that bubble out of any of the crypto helpers.
    enum CryptoError: Error, CustomStringConvertible {
        case encryptionFailed(String)
        case decryptionFailed(String)
        case signingFailed(String)
        case invalidBase64(String)
        case keyParseFailed(String)
        case keyAgreementFailed(String)
        case invalidFormat

        var description: String {
            switch self {
            case .encryptionFailed(let m):   return "Encryption failed: \(m)"
            case .decryptionFailed(let m):   return "Decryption failed: \(m)"
            case .signingFailed(let m):      return "Signing failed: \(m)"
            case .invalidBase64(let m):      return "Invalid Base64: \(m)"
            case .keyParseFailed(let m):     return "Key parse failed: \(m)"
            case .keyAgreementFailed(let m): return "Key agreement failed: \(m)"
            case .invalidFormat:             return "Invalid format"
            }
        }
    }

    /// ECIES-encrypts [data] and returns the Tink-format base64 blob.
    static func encryptToTinkBlob(_ data: Data, publicKey: P256.KeyAgreement.PublicKey) throws -> String {
        return KeyEncoding.base64Encode(try EciesCrypto.encrypt(data, publicKey: publicKey))
    }

    /// Decrypts a base64 Tink blob back to plaintext.
    static func decryptFromTinkBlob(_ tinkBlob: String, privateKey: P256.KeyAgreement.PrivateKey) throws -> Data {
        return try EciesCrypto.decrypt(KeyEncoding.base64Decode(tinkBlob), privateKey: privateKey)
    }

    /// URL-safe base64.
    static func base64Encode(_ data: Data) -> String {
        return KeyEncoding.base64Encode(data)
    }

    /// Parses a P-256 public key from PEM (SPKI) text.
    static func parsePublicKey(from pem: String) throws -> P256.KeyAgreement.PublicKey {
        return try KeyEncoding.importPublicKeyFromPEM(pem)
    }
}
