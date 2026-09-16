//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

/**
 * POST /api/session/token — exchanges a signed-and-encrypted envelope for a
 * session token, using the keys owned by this `AttestationSession`.
 *
 * Steps (match Android's `AttestationSession.fetchSessionToken`):
 *   1. Encrypt payload with the server pubkey (ECIES, Tink blob format)
 *   2. Sign the encrypted blob with the auth privkey (ECDSA-SHA256)
 *   3. App Attest assertion over the SAME blob
 *   4. POST
 *   5. Decrypt response with the ephemeral encryption privkey
 *
 * Authentication is via the auth-cert signature, NOT via fresh attestation
 * — attestation only happens at cert registration. The server still requires
 * an ongoing assertion so it can re-bind the request to the registered
 * App Attest key.
 */
extension AttestationSession {
    public func fetchSessionToken() async -> SessionTokenResult {
        do {
            // 1. Keys (owned by this session).
            guard let ephemeralEncryptionPrivateKey = ephemeralEncryptionPrivateKey else {
                return .error(code: -1, message: "Ephemeral client encryption private key not available.")
            }
            let authPrivateKey = try await CertificateManager.shared.getPrivateKey(deviceUUID: deviceUUID)

            // 2. Encrypt + sign payload.
            let payload: [String: Any] = [
                "challengeHash": challengeHash,
                "clientId": clientId,
                "system": Self.system
            ]
            let payloadBytes = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
            let encryptedRequestData = try CryptoHelper.encryptToTinkBlob(payloadBytes, publicKey: serverEncryptionPublicKey)
            let blob = encryptedRequestData.data(using: .utf8)!
            let signatureBytes = try authPrivateKey.signature(for: blob).derRepresentation

            // 3. App Attest assertion over the blob. Server verifies
            // ECDSA(credCertPubKey, sig, SHA-256(authData || SHA-256(blob))).
            let assertion: Data
            do {
                assertion = try await AppAttestManager.shared.generateOngoingAssertionAsync(over: blob)
            } catch DeviceAttestationError.notSupported {
                return .error(code: -1, message: "App Attest keyId missing; cannot generate ongoing assertion")
            }

            // 4. Send.
            let requestBody: [String: Any] = [
                "encryptedData": encryptedRequestData,
                "signature": CryptoHelper.base64Encode(signatureBytes),
                "assertion": assertion.base64EncodedString()
            ]
            let base = try await DeviceAttestation.shared.sessionTokenUrl
            guard let url = makeAttestationURL(base: base, queryItems: [
                URLQueryItem(name: "s", value: sessionId)
            ]) else {
                return .error(code: -1, message: "Invalid session token URL")
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.timeoutInterval = 5.0
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: requestBody)

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? -1
                let msg = String(data: data, encoding: .utf8) ?? "Unknown error"
                return .error(code: code, message: msg)
            }

            // 5. Decrypt response.
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            guard let encryptedResponseData = json?["encryptedData"] as? String else {
                return .error(code: -1, message: "Malformed session token response")
            }

            let tokenBytes = try CryptoHelper.decryptFromTinkBlob(encryptedResponseData, privateKey: ephemeralEncryptionPrivateKey)
            // Decrypted plaintext is AEAD-authenticated (no MITM forgery), but a
            // buggy/rogue server or a future format change could still return
            // non-UTF-8 bytes — validate instead of force-unwrapping so we return
            // .error rather than crashing.
            guard String(data: tokenBytes, encoding: .utf8) != nil else {
                return .error(code: -1, message: "Session token response was not valid UTF-8")
            }
            let tokenResponse = try JSONSerialization.jsonObject(with: tokenBytes) as? [String: Any]
            guard let token = tokenResponse?["token"] as? String else {
                return .error(code: -1, message: "Malformed session token payload")
            }
            return .success(token)
        } catch let DeviceAttestationError.configurationError(message) {
            return .error(code: -1, message: message)
        } catch {
            return .exception(error)
        }
    }
}
