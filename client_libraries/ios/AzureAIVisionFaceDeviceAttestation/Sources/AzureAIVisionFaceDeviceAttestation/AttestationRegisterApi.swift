//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation

/**
 * POST /api/attestation/register — registers a new auth cert + App Attest proofs.
 *
 * Wire format matches Android except for `attestJson`:
 *   - Android `attestJson = { token: <play integrity JWT>, certificateChain: [...] }`
 *   - iOS     `attestJson = { attestation: <CBOR>, assertion: <CBOR> }`
 *
 * Server (ios_auth.ts: IosAttestJson) requires BOTH `attestation` and
 * `assertion`; missing `assertion` is rejected with MISSING_ASSERTION.
 *
 *   Request body:
 *   {
 *     "payload":        "{\"challengeHash\":..., \"encryptionPublicCert\":..., \"attestJson\":\"...\"}",
 *     "authPublicCert": "-----BEGIN CERTIFICATE-----\n...",
 *     "signature":      "<base64 ECDSA over payload bytes>"
 *   }
 *
 *   Response:
 *   { "message": String, "serverEncryptionPublicKey": String }
 */
func registerAttestation(
    sParam: String,
    clientId: String,
    system: String,
    challengeHash: String,
    deviceUUID: String,
    attestationToken: String,
    assertionToken: String,
    completion: @escaping (Result<AttestationRegisterResult, DeviceAttestationError>) -> Void
) {
    Task {
        do {
            // 1. Ephemeral encryption cert.
            let ephemeralEncryptionCert = try await CertificateManager.shared.generateEphemeralEncryptionCert()

            // 2. Auth cert (long-lived).
            let authCertPEM = try await CertificateManager.shared.getCertificatePEM(deviceUUID: deviceUUID)
            let authPrivateKey = try await CertificateManager.shared.getPrivateKey(deviceUUID: deviceUUID)

            // 3. attestJson wraps the two App Attest CBOR blobs as a JSON string.
            // The naming differs from Android (`attestation`/`assertion` vs
            // Android's `token`) because here the bytes describe what they
            // actually are — Android reuses `token` for its Play Integrity JWT.
            let attestJsonDict: [String: Any] = [
                "attestation": attestationToken,
                "assertion": assertionToken
            ]
            let attestJsonData = try JSONSerialization.data(withJSONObject: attestJsonDict)
            let attestJson = String(data: attestJsonData, encoding: .utf8)!

            // 4. Build and sign payload.
            let payloadDict: [String: Any] = [
                "challengeHash": challengeHash,
                "encryptionPublicCert": ephemeralEncryptionCert.certificatePEM,
                "attestJson": attestJson
            ]
            let payloadData = try JSONSerialization.data(withJSONObject: payloadDict, options: [.sortedKeys])
            let payloadString = String(data: payloadData, encoding: .utf8)!
            let signatureBytes = try authPrivateKey.signature(for: payloadData).derRepresentation

            // 5. Build request body + send.
            let requestBody: [String: Any] = [
                "payload": payloadString,
                "authPublicCert": authCertPEM,
                "signature": CryptoHelper.base64Encode(signatureBytes)
            ]

            let base = try await DeviceAttestation.shared.attestationRegisterUrl
            guard let url = makeAttestationURL(base: base, queryItems: [
                URLQueryItem(name: "s", value: sParam),
                URLQueryItem(name: "cid", value: clientId),
                URLQueryItem(name: "sys", value: system)
            ]) else {
                completion(.failure(.invalidResponse))
                return
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
                await CertificateManager.shared.deleteEphemeralEncryptionCert(ephemeralEncryptionCert.privateKey)
                completion(.failure(.networkError(code, msg)))
                return
            }

            // 6. Parse response.
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            guard let serverEncryptionPublicKey = json?["serverEncryptionPublicKey"] as? String else {
                await CertificateManager.shared.deleteEphemeralEncryptionCert(ephemeralEncryptionCert.privateKey)
                completion(.failure(.serializationError))
                return
            }

            completion(.success(AttestationRegisterResult(
                serverEncryptionPublicKey: serverEncryptionPublicKey,
                ephemeralEncryptionPrivateKey: ephemeralEncryptionCert.privateKey
            )))
        } catch {
            completion(.failure(.cryptoError(error.localizedDescription)))
        }
    }
}
