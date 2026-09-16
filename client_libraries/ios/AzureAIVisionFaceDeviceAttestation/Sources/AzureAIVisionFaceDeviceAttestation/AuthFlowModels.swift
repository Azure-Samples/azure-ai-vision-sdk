//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

// MARK: - Error types

enum DeviceAttestationError: Error {
    case networkError(Int, String)
    case configurationError(String)
    case serializationError
    case attestationError(Error)
    case invalidResponse
    case cryptoError(String)
    case keyGenerationFailed
    case attestationFailed
    case assertionFailed
    case notSupported
}

// MARK: - Result types

/// Outcome of `verifyAttestation` — whether the server already knows our auth cert,
/// and (if yes) the server's encryption pubkey + our ephemeral encryption privkey
/// to hand to the `AttestationSession`.
struct AttestationVerifyResult {
    let certExists: Bool
    let serverEncryptionPublicKey: String?
    let ephemeralEncryptionPrivateKey: P256.KeyAgreement.PrivateKey?
}

/// Outcome of a successful `registerAttestation` call.
struct AttestationRegisterResult {
    let serverEncryptionPublicKey: String
    let ephemeralEncryptionPrivateKey: P256.KeyAgreement.PrivateKey
}

// MARK: - URL building

/// Builds an endpoint URL from `base` plus the ordered `queryItems`,
/// percent-encoding every value via `URLComponents` / `URLQueryItem`.
///
/// Encoding is essential, not cosmetic: values such as the Universal Link `s`
/// session id reach the client already percent-decoded (they come off the
/// inbound link via `URLComponents.queryItems`), so interpolating one straight
/// into a query string would let a crafted value inject extra `&name=value`
/// pairs ("query smuggling") — e.g. a second `cid` that a first-value-wins
/// server honors over the real one. `URLQueryItem` folds any `&`, `=`, `#`
/// inside a value down to a single opaque parameter; `URL(string:)` alone would
/// not, since those are all valid URL characters and it would build the
/// injected URL rather than returning nil.
///
/// Returns nil only when `base` itself is not a valid URL string — the same
/// failure the callers already handle where they previously used `URL(string:)`.
func makeAttestationURL(base: String, queryItems: [URLQueryItem]) -> URL? {
    guard var components = URLComponents(string: base) else { return nil }
    components.queryItems = queryItems
    return components.url
}
