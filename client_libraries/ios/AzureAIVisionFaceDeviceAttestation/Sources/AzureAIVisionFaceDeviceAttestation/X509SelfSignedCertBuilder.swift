//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation

/**
 * Builds a self-signed X.509 v3 certificate around a P-256 keypair.
 *
 * Used by both the long-lived auth-cert path in [CertificateManager] and the
 * one-shot ephemeral encryption certs. Factored out into a free utility
 * because none of these functions touch the actor's mutable state — every
 * input is passed in explicitly.
 *
 * The implementation hand-emits ASN.1 DER (vs. depending on the
 * swift-certificates package) because the sample keeps its dependency
 * surface minimal, and the resulting bytes need to round-trip through
 * OpenSSL on the server side.
 *
 * The signing key never leaves the caller: `build` takes the public key in
 * X9.63 form plus a `sign` closure that ECDSA-signs the to-be-signed bytes
 * and returns the signature already DER-encoded as `ECDSA-Sig-Value
 * SEQUENCE { r INTEGER, s INTEGER }` (exactly what CryptoKit's
 * `ECDSASignature.derRepresentation` yields). That lets Secure-Enclave-backed
 * keys and software keys share one builder.
 *
 * A previous revision of this generator emitted invalid DER in three places
 * (SEQUENCE-wrapped version, negative serial, UTCTime past 2049) that
 * OpenSSL silently refuses to parse; the inline comments below call out the
 * specific fixes.
 */
enum X509SelfSignedCertBuilder {

    /// Builds a DER-encoded X.509 v3 self-signed certificate for the given
    /// public key, subject, and validity window.
    ///
    /// - Parameters:
    ///   - subject: RFC 4514-ish string, e.g. `"CN=Foo, O=Bar"`. Only `CN`
    ///     and `O` attributes are emitted; anything else is ignored.
    ///   - publicKeyX963: the P-256 public key in uncompressed X9.63 form
    ///     (`0x04 || X || Y`, 65 bytes) — CryptoKit's `x963Representation`.
    ///   - notBefore / notAfter: validity window.
    ///   - sign: ECDSA-SHA256-signs the passed TBS bytes and returns the
    ///     signature DER-encoded as `ECDSA-Sig-Value`.
    static func build(
        subject: String,
        publicKeyX963: Data,
        notBefore: Date,
        notAfter: Date,
        sign: (Data) throws -> Data
    ) throws -> Data {
        var tbsCertificate = Data()

        // X.509 v3 version: [0] EXPLICIT INTEGER 2 — context-specific tag
        // 0xA0, wrapping INTEGER 2. Wrapping in a SEQUENCE (0x30) instead,
        // which an earlier version did, makes OpenSSL refuse to parse the
        // cert at all.
        tbsCertificate.append(Data([0xA0, 0x03, 0x02, 0x01, 0x02]))

        // Positive non-zero serial per RFC 5280 §4.1.2.2.
        var serialBytes = (0..<8).map { _ in UInt8.random(in: 0...255) }
        serialBytes[0] &= 0x7F                  // clear sign bit
        if serialBytes[0] == 0 { serialBytes[0] = 1 }  // non-zero
        tbsCertificate.append(Data([0x02, 0x08] + serialBytes))

        let signatureAlgorithm = algorithmIdentifier()
        let name = makeName(subject: subject)

        tbsCertificate.append(signatureAlgorithm)
        tbsCertificate.append(name)                                       // issuer
        tbsCertificate.append(makeValidity(notBefore: notBefore, notAfter: notAfter))
        tbsCertificate.append(name)                                       // subject (same)
        tbsCertificate.append(makeSubjectPublicKeyInfo(publicKeyData: publicKeyX963))

        let tbsSequence = makeSequence(tbsCertificate)
        let signature = try sign(tbsSequence)

        var fullCertificate = Data()
        fullCertificate.append(tbsSequence)
        fullCertificate.append(signatureAlgorithm)
        fullCertificate.append(makeBitString(signature))

        return makeSequence(fullCertificate)
    }

    // ------------------------------------------------------------------
    // ASN.1 DER primitives
    // ------------------------------------------------------------------

    private static func makeSequence(_ data: Data) -> Data {
        var result = Data([0x30])
        result.append(encodeLength(data.count))
        result.append(data)
        return result
    }

    private static func makeSet(_ data: Data) -> Data {
        var result = Data([0x31])
        result.append(encodeLength(data.count))
        result.append(data)
        return result
    }

    private static func makeBitString(_ data: Data) -> Data {
        var result = Data([0x03])
        result.append(encodeLength(data.count + 1))
        result.append(0x00)
        result.append(data)
        return result
    }

    /// DER length encoding: short form for <128, 0x81-prefixed for <256,
    /// 0x82-prefixed for the two-byte case (all this generator ever needs).
    private static func encodeLength(_ length: Int) -> Data {
        if length < 128 {
            return Data([UInt8(length)])
        } else if length < 256 {
            return Data([0x81, UInt8(length)])
        } else {
            let byte1 = UInt8((length >> 8) & 0xFF)
            let byte2 = UInt8(length & 0xFF)
            return Data([0x82, byte1, byte2])
        }
    }

    // ------------------------------------------------------------------
    // X.509 structure pieces
    // ------------------------------------------------------------------

    private static func algorithmIdentifier() -> Data {
        // ecdsaWithSHA256 OID: 1.2.840.10045.4.3.2
        //
        // RFC 5758 §3.2: "When the ecdsa-with-SHA256 algorithm identifier
        // appears in the algorithm field as an AlgorithmIdentifier, the
        // encoding MUST omit the parameters field." Older code appended
        // NULL (0x05 0x00); strict parsers reject it.
        let ecdsaWithSHA256 = Data([0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x02])
        return makeSequence(ecdsaWithSHA256)
    }

    private static func makeName(subject: String) -> Data {
        let components = subject.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        var rdnSequence = Data()

        for component in components {
            let parts = component.split(separator: "=")
            guard parts.count == 2 else { continue }

            let attribute = String(parts[0]).trimmingCharacters(in: .whitespaces)
            let value = String(parts[1]).trimmingCharacters(in: .whitespaces)

            let oid: Data
            switch attribute {
            case "CN":  oid = Data([0x06, 0x03, 0x55, 0x04, 0x03])
            case "O":   oid = Data([0x06, 0x03, 0x55, 0x04, 0x0a])
            default:    continue
            }

            let utf8String = Data([0x0c, UInt8(value.utf8.count)] + value.utf8)
            let attributeValueAssertion = makeSequence(oid + utf8String)
            let relativeDistinguishedName = makeSet(attributeValueAssertion)
            rdnSequence.append(relativeDistinguishedName)
        }

        return makeSequence(rdnSequence)
    }

    private static func makeValidity(notBefore: Date, notAfter: Date) -> Data {
        return makeSequence(makeTime(notBefore) + makeTime(notAfter))
    }

    /// RFC 5280 §4.1.2.5: years <= 2049 must use UTCTime (YY two-digit year),
    /// years >= 2050 must use GeneralizedTime (YYYY four-digit year). A
    /// 25-year validity that rolls past 2050 needs GeneralizedTime or UTCTime
    /// parsers will read "51" as 1951 and the cert appears long-expired.
    private static func makeTime(_ date: Date) -> Data {
        let calendar = Calendar(identifier: .gregorian)
        let year = calendar.component(.year, from: date)
        let formatter = DateFormatter()
        formatter.timeZone = TimeZone(identifier: "UTC")
        // Pin locale and calendar so rendering never depends on the device
        // defaults. Without this, `DateFormatter` uses the user's calendar and
        // locale: a non-Gregorian calendar (e.g. Thai Buddhist) emits the wrong
        // era-year (2025 -> "2568", UTCTime keeps "68" -> parsed as 1968, cert
        // looks long-expired), and Arabic/Farsi locales render Eastern-Arabic
        // digits (e.g. "٢٠٢٥") which are non-ASCII — violating the
        // UTCTime/GeneralizedTime ASCII requirement and making the length byte
        // below disagree with the visible length, corrupting the DER.
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)

        let tag: UInt8
        if year >= 2050 || year < 1950 {
            formatter.dateFormat = "yyyyMMddHHmmss"   // GeneralizedTime
            tag = 0x18
        } else {
            formatter.dateFormat = "yyMMddHHmmss"     // UTCTime
            tag = 0x17
        }

        let timeString = formatter.string(from: date) + "Z"
        // ASN.1 time values are ASCII-only, and the single length byte below
        // encodes `utf8.count`, which only matches the visible length for
        // single-byte characters. `en_US_POSIX` + Gregorian guarantees this;
        // assert it so any future regression fails loudly instead of silently
        // emitting an invalid/expired certificate.
        precondition(timeString.allSatisfy { $0.isASCII },
                     "X.509 time must be ASCII, got: \(timeString)")
        return Data([tag, UInt8(timeString.utf8.count)] + timeString.utf8)
    }

    private static func makeSubjectPublicKeyInfo(publicKeyData: Data) -> Data {
        // ecPublicKey OID: 1.2.840.10045.2.1
        let ecPublicKeyOID = Data([0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01])
        // prime256v1 (secp256r1 / P-256) OID: 1.2.840.10045.3.1.7
        let prime256v1OID = Data([0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07])
        let algorithm = makeSequence(ecPublicKeyOID + prime256v1OID)

        // publicKeyData is already X9.63 (0x04 || X || Y).
        let publicKeyBitString = makeBitString(publicKeyData)
        return makeSequence(algorithm + publicKeyBitString)
    }
}
