//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import Security
import CryptoKit

/**
 * Owns the device's long-lived auth cert (Secure Enclave-backed via
 * CryptoKit) plus the per-session ephemeral encryption certs.
 *
 * Implementation split:
 *
 *   CertificateManager.swift                  this file — public API + alias helpers
 *   CertificateManager+AuthCertInternals.swift internals for auth-cert lifecycle
 *   CertificateManager+EphemeralCert.swift    ephemeral encryption certs
 *   CertificateError.swift                    error type
 *
 * Lives as an `actor` because keychain mutations + thumbprint cache are
 * shared mutable state.
 *
 * Key storage layout after the SecureEnclave migration:
 *
 *   Auth privkey blob   → kSecClassGenericPassword, tag "<alias>_se_blob"
 *   Auth cert (DER)     → kSecClassGenericPassword, tag "<alias>_cert"
 *
 * (Old kSecClassKey-backed records from before this migration become
 * unreadable; `ensureKeyPair` detects the gap and re-registers.)
 */
actor CertificateManager {
    static let shared = CertificateManager()

    let keyAliasPrefix = "azure_vision_liveness_"
    let certValidityYears = 25

    /// deviceUUID → hex SHA-256 thumbprint of the cert. Avoids re-reading
    /// + re-hashing on every signed request.
    var thumbprintCache: [String: String] = [:]

    private init() {}

    // ------------------------------------------------------------------
    // Alias / tag helpers (used by every extension file)
    // ------------------------------------------------------------------

    func getAlias(deviceUUID: String) -> String {
        return "\(keyAliasPrefix)\(deviceUUID)"
    }

    /// Keychain account tag for the SE-key dataRepresentation blob.
    func getSEKeyBlobTag(alias: String) -> String {
        return "\(alias)_se_blob"
    }

    /// Keychain account tag for the auth cert (DER bytes).
    func getCertificateTag(alias: String) -> String {
        return "\(alias)_cert"
    }

    // ------------------------------------------------------------------
    // Auth cert lifecycle (public)
    // ------------------------------------------------------------------

    /**
     * Returns the SE-backed auth signing key for [deviceUUID], creating one
     * (plus a fresh self-signed cert) if missing.
     *
     * If the stored cert is missing, malformed, or the SE blob no longer
     * loads (which is what happens for existing devices on first launch
     * after the SecureEnclave migration), this wipes the alias and rebuilds.
     */
    func ensureKeyPair(deviceUUID: String) async throws -> SecureEnclave.P256.Signing.PrivateKey {
        let alias = getAlias(deviceUUID: deviceUUID)

        if let existing = try? loadAuthSigningKey(alias: alias),
           try certificateExists(alias: alias) {
            return existing
        }

        try? await deleteCertificate(deviceUUID: deviceUUID)
        return try generateAuthKeyAndCert(alias: alias)
    }

    /// True if [deviceUUID] has an auth cert in the keychain. Throws on a
    /// transient keychain failure rather than reporting a false negative.
    func hasCertificate(deviceUUID: String) async throws -> Bool {
        return try certificateExists(alias: getAlias(deviceUUID: deviceUUID))
    }

    /// DER-encoded auth cert bytes. Throws if no cert exists for [deviceUUID].
    func getCertificateDER(deviceUUID: String) async throws -> Data {
        return try readKeychainData(account: getCertificateTag(alias: getAlias(deviceUUID: deviceUUID))) ?? {
            throw CertificateError.certificateNotFound(deviceUUID: deviceUUID)
        }()
    }

    /// Auth cert as PEM. Convenience wrapper around [getCertificateDER].
    func getCertificatePEM(deviceUUID: String) async throws -> String {
        let der = try await getCertificateDER(deviceUUID: deviceUUID)
        return derToPEMCertificate(der)
    }

    /// Hex-encoded SHA-256 thumbprint of the auth cert. Cached after first call.
    func getCertificateThumbprint(deviceUUID: String) async throws -> String {
        if let cached = thumbprintCache[deviceUUID] {
            return cached
        }
        let der = try await getCertificateDER(deviceUUID: deviceUUID)
        let hash = SHA256.hash(data: der)
        let thumbprint = hash.map { String(format: "%02x", $0) }.joined()
        thumbprintCache[deviceUUID] = thumbprint
        return thumbprint
    }

    /// Returns the SE-backed signing key. Throws if missing.
    func getPrivateKey(deviceUUID: String) async throws -> SecureEnclave.P256.Signing.PrivateKey {
        return try loadAuthSigningKey(alias: getAlias(deviceUUID: deviceUUID))
    }

    /// Deletes the auth cert + SE key blob for [deviceUUID] and clears its
    /// cached thumbprint.
    func deleteCertificate(deviceUUID: String) async throws {
        let alias = getAlias(deviceUUID: deviceUUID)
        deleteKeychainItem(account: getSEKeyBlobTag(alias: alias))
        deleteKeychainItem(account: getCertificateTag(alias: alias))
        thumbprintCache.removeValue(forKey: deviceUUID)
    }

    // ------------------------------------------------------------------
    // Keychain helpers (shared by extensions in other files)
    // ------------------------------------------------------------------

    /// Reads a keychain `GenericPassword` item by account tag.
    ///
    /// Returns `nil` ONLY when the item is genuinely absent (`errSecItemNotFound`).
    /// Any other non-success status (e.g. `errSecInteractionNotAllowed` while the
    /// device is locked) is a transient/unexpected failure that must NOT be read
    /// as "no cert" — doing so would push the flow into a re-register it didn't
    /// need — so it throws instead.
    func readKeychainData(account: String) throws -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        switch status {
        case errSecSuccess:
            return result as? Data
        case errSecItemNotFound:
            return nil
        default:
            throw CertificateError.keychainOperationFailed(status: status)
        }
    }

    /// Writes `data` under `account` as a `GenericPassword`, overwriting any
    /// existing value.
    ///
    /// `SecItemAdd` returns `errSecDuplicateItem` when an item already exists for
    /// the account and does NOT update it — so without handling that, a freshly
    /// generated cert / SE-key blob would be silently dropped and the stale one
    /// reused while the caller believes the write succeeded. Registration isn't
    /// always preceded by a delete, so on a duplicate we `SecItemUpdate` the
    /// stored value to match what was just generated.
    func writeKeychainData(_ data: Data, account: String) throws {
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data
        ]
        let status = SecItemAdd(attributes as CFDictionary, nil)
        switch status {
        case errSecSuccess:
            return
        case errSecDuplicateItem:
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrAccount as String: account
            ]
            let update: [String: Any] = [kSecValueData as String: data]
            let updateStatus = SecItemUpdate(query as CFDictionary, update as CFDictionary)
            guard updateStatus == errSecSuccess else {
                throw CertificateError.keychainOperationFailed(status: updateStatus)
            }
        default:
            throw CertificateError.keychainOperationFailed(status: status)
        }
    }

    /// Deletes a `GenericPassword` keychain item by account tag (no-op if absent).
    func deleteKeychainItem(account: String) {
        SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account
        ] as CFDictionary)
    }

    /// Convenience: true if the cert with the given alias exists in keychain.
    /// Throws (rather than returning false) on a transient keychain failure, so
    /// callers don't mistake "couldn't read" for "not registered".
    func certificateExists(alias: String) throws -> Bool {
        try readKeychainData(account: getCertificateTag(alias: alias)) != nil
    }

    /// Loads the SE-backed auth signing key from the stored dataRepresentation
    /// blob. Throws if the blob is missing or no longer loads.
    func loadAuthSigningKey(alias: String) throws -> SecureEnclave.P256.Signing.PrivateKey {
        guard let blob = try readKeychainData(account: getSEKeyBlobTag(alias: alias)) else {
            throw CertificateError.keyNotFound(deviceUUID: alias)
        }
        return try SecureEnclave.P256.Signing.PrivateKey(dataRepresentation: blob)
    }

    /**
     * DER → PEM with care taken to emit a final newline.
     *
     * `.lineLength64Characters + .endLineWithLineFeed` doesn't emit a
     * trailing newline after the final partial line, so without an explicit
     * `"\n"` the END marker would get glued onto the last base64 chunk and
     * OpenSSL would reject it with "PEM routines :: bad end line".
     */
    func derToPEMCertificate(_ derData: Data) -> String {
        let base64 = derData.base64EncodedString(options: [.lineLength64Characters, .endLineWithLineFeed])
        return "-----BEGIN CERTIFICATE-----\n\(base64)\n-----END CERTIFICATE-----\n"
    }
}
