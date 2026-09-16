//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import CryptoKit

/**
 * POST /api/attestation/verify — asks whether the server already knows our auth cert.
 *
 * Wire format matches Android:
 *
 *   Request body:
 *   {
 *     "payload":        "{\"challengeHash\":\"...\",\"encryptionPublicCert\":\"...\"}",
 *     "authPublicCert": "-----BEGIN CERTIFICATE-----\n...",
 *     "signature":      "<base64 ECDSA over payload bytes>",
 *     "assertion":      "<base64 App Attest assertion over payload bytes>"
 *   }
 *
 *   Response (plain JSON):
 *   { "exists": Bool, "serverEncryptionPublicKey": String? }
 *
 * App Attest assertion: server (ios_auth.ts: verifyIosOngoingAssertion)
 * computes clientDataHash = SHA-256(payloadBytes) and verifies
 * ECDSA(credCertPubKey, sig, SHA-256(authData || clientDataHash)). This
 * function is called only from the keyExisted branch in
 * `AttestationFlow.obtainSessionKeys`, which `startSession` gates on
 * `AppAttestManager.hasAttestedKey()` — so an attested keyId is guaranteed to
 * be in UserDefaults here. (After an app reinstall wipes UserDefaults while the
 * Keychain auth cert survives, startSession deletes the stale cert and
 * re-registers instead of reaching this path.)
 */
func verifyAttestation(
    sParam: String,
    clientId: String,
    system: String,
    challengeHash: String,
    deviceUUID: String,
    completion: @escaping (Result<AttestationVerifyResult, DeviceAttestationError>) -> Void
) {
    Task {
        do {
            // 1. Ephemeral encryption cert for THIS session (returned later on success).
            let ephemeralEncryptionCert = try await CertificateManager.shared.generateEphemeralEncryptionCert()

            // 2. Auth cert + matching private key (long-lived, Secure-Enclave).
            let authCertPEM = try await CertificateManager.shared.getCertificatePEM(deviceUUID: deviceUUID)
            let authPrivateKey = try await CertificateManager.shared.getPrivateKey(deviceUUID: deviceUUID)

            // 3. Build and sign payload.
            let payloadDict: [String: Any] = [
                "challengeHash": challengeHash,
                "encryptionPublicCert": ephemeralEncryptionCert.certificatePEM
            ]
            let payloadData = try JSONSerialization.data(withJSONObject: payloadDict, options: [.sortedKeys])
            let payloadString = String(data: payloadData, encoding: .utf8)!
            let signatureBytes = try authPrivateKey.signature(for: payloadData).derRepresentation

            // 4. App Attest assertion over the SAME bytes the auth signature signs.
            // The verify path only runs when startSession confirmed an attested
            // key exists (keyExisted gates it on `hasAttestedKey()`), so the
            // cached keyId is guaranteed present here.
            let assertion = try await AppAttestManager.shared.generateOngoingAssertionAsync(over: payloadData)

            // 5. Build request body + send.
            let requestBody: [String: Any] = [
                "payload": payloadString,
                "authPublicCert": authCertPEM,
                "signature": CryptoHelper.base64Encode(signatureBytes),
                "assertion": assertion.base64EncodedString()
            ]

            let base = try await DeviceAttestation.shared.attestationVerifyUrl
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
            guard let exists = json?["exists"] as? Bool else {
                await CertificateManager.shared.deleteEphemeralEncryptionCert(ephemeralEncryptionCert.privateKey)
                completion(.failure(.serializationError))
                return
            }

            if exists {
                guard let serverEncryptionPublicKey = json?["serverEncryptionPublicKey"] as? String else {
                    await CertificateManager.shared.deleteEphemeralEncryptionCert(ephemeralEncryptionCert.privateKey)
                    completion(.failure(.serializationError))
                    return
                }
                completion(.success(AttestationVerifyResult(
                    certExists: exists,
                    serverEncryptionPublicKey: serverEncryptionPublicKey,
                    ephemeralEncryptionPrivateKey: ephemeralEncryptionCert.privateKey
                )))
            } else {
                // No registration coming; ephemeral cert is dead weight.
                await CertificateManager.shared.deleteEphemeralEncryptionCert(ephemeralEncryptionCert.privateKey)
                completion(.success(AttestationVerifyResult(
                    certExists: exists,
                    serverEncryptionPublicKey: nil,
                    ephemeralEncryptionPrivateKey: nil
                )))
            }
        } catch {
            completion(.failure(.cryptoError(error.localizedDescription)))
        }
    }
}
