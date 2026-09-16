//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

/**
 * PEM / base64 helpers for the cert-based auth flow.
 *
 * After the SecureEnclave + CryptoKit migration, all keys flow through
 * CryptoKit's P256 types (`P256.KeyAgreement.PublicKey` /
 * `SecureEnclave.P256.Signing.PrivateKey` / `P256.Signing.PrivateKey`).
 * SPKI wrapping is delegated to CryptoKit's `derRepresentation`.
 */
enum KeyEncoding {

    private static let PEM_PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----"
    private static let PEM_PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----"

    /// Wraps a CryptoKit P-256 public key into a PEM string the server can
    /// read. `derRepresentation` returns SPKI-encoded DER directly.
    static func exportPublicKeyToPEM(_ publicKey: P256.KeyAgreement.PublicKey) -> String {
        let base64 = publicKey.derRepresentation
            .base64EncodedString(options: [.lineLength64Characters])
        return "\(PEM_PUBLIC_KEY_HEADER)\n\(base64)\n\(PEM_PUBLIC_KEY_FOOTER)"
    }

    /// Imports a CryptoKit P-256 public key from a PEM string. CryptoKit's
    /// `init(derRepresentation:)` understands SPKI.
    static func importPublicKeyFromPEM(_ pem: String) throws -> P256.KeyAgreement.PublicKey {
        let base64 = pem
            .replacingOccurrences(of: PEM_PUBLIC_KEY_HEADER, with: "")
            .replacingOccurrences(of: PEM_PUBLIC_KEY_FOOTER, with: "")
            .replacingOccurrences(of: "\n", with: "")
            .replacingOccurrences(of: "\r", with: "")
            .trimmingCharacters(in: .whitespaces)

        guard let der = Data(base64Encoded: base64) else {
            throw CryptoHelper.CryptoError.invalidFormat
        }
        return try P256.KeyAgreement.PublicKey(derRepresentation: der)
    }

    /// URL-safe base64, no padding. Matches `CryptoHelper.base64Encode` /
    /// Android's `CryptoHelper.base64Encode`.
    static func base64Encode(_ data: Data) -> String {
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// Accepts both standard and URL-safe base64.
    static func base64Decode(_ string: String) throws -> Data {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }
        guard let data = Data(base64Encoded: base64) else {
            throw CryptoHelper.CryptoError.invalidBase64("Failed to decode Base64 string")
        }
        return data
    }
}
