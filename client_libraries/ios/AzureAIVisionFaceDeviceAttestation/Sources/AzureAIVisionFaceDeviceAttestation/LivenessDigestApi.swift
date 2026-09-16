//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

/**
 * POST /api/liveness/digest — submits the attestation digest emitted at the end
 * of the liveness flow, using the keys owned by this `AttestationSession`.
 *
 * Mirrors Android's `AttestationSession.submitLivenessDigest`. Uses the same
 * encrypt-and-sign envelope as `fetchSessionToken`, so the server's `digest`
 * endpoint handles both platforms with one code path.
 *
 *   Plaintext payload:  `{ "cid": clientId, "os": "ios", "digest": digest }`
 *   Plaintext response: `{ "success": true }`
 *
 * This is the final call in the flow: once it returns (success or failure),
 * the ephemeral key is dropped and the session is released automatically.
 */
extension AttestationSession {
    public func submitLivenessDigest(_ digest: String) async -> LivenessDigestResult {
        let result = await postDigest(digest)
        // Digest is the last call in the flow — drop the ephemeral key and
        // release this session so callers don't have to.
        await cleanup()
        return result
    }

    private func postDigest(_ digest: String) async -> LivenessDigestResult {
        guard !digest.isEmpty else {
            return .error(code: -1, message: "digest is empty")
        }

        do {
            // 1. Keys (owned by this session).
            guard let ephemeralEncryptionPrivateKey = ephemeralEncryptionPrivateKey else {
                return .error(code: -1, message: "Ephemeral client encryption private key not available.")
            }
            let authPrivateKey = try await CertificateManager.shared.getPrivateKey(deviceUUID: deviceUUID)

            // 2. Encrypt + sign payload.
            let payload: [String: Any] = [
                "cid": clientId,
                "os": Self.system,
                "digest": digest
            ]
            let payloadBytes = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
            let encryptedRequestData = try CryptoHelper.encryptToTinkBlob(payloadBytes, publicKey: serverEncryptionPublicKey)
            let blob = encryptedRequestData.data(using: .utf8)!
            let signatureBytes = try authPrivateKey.signature(for: blob).derRepresentation

            // 3. App Attest assertion.
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

            let base = try await DeviceAttestation.shared.livenessDigestUrl
            guard let url = makeAttestationURL(base: base, queryItems: [
                URLQueryItem(name: "s", value: sessionId)
            ]) else {
                return .error(code: -1, message: "Invalid liveness digest URL")
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

            // 5. Decrypt and parse response.
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            guard let encryptedResponseData = json?["encryptedData"] as? String else {
                return .error(code: -1, message: "Malformed digest response")
            }

            let decryptedBytes = try CryptoHelper.decryptFromTinkBlob(encryptedResponseData, privateKey: ephemeralEncryptionPrivateKey)
            let decryptedResponseString = String(data: decryptedBytes, encoding: .utf8) ?? ""
            let parsed = try JSONSerialization.jsonObject(with: Data(decryptedResponseString.utf8)) as? [String: Any]

            if let success = parsed?["success"] as? Bool, success {
                return .success
            } else {
                return .error(code: -1, message: "Digest post returned success=false: \(decryptedResponseString)")
            }
        } catch let DeviceAttestationError.configurationError(message) {
            return .error(code: -1, message: message)
        } catch {
            return .exception(error)
        }
    }
}
