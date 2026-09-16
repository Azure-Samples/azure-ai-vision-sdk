//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation

/// POST /api/attestation/challenge — kicks off a session and returns the server's
/// `challengeHash`. All downstream calls bind this hash into their App Attest
/// and ECDSA signatures so the server can prove session freshness.
///
/// This endpoint has no authentication of its own; it's the bootstrap step
/// before the cert-based handshake.
func fetchAttestationChallenge(sParam: String, clientId: String, completion: @escaping (Result<String, DeviceAttestationError>) -> Void) {
    Task {
        do {
            let base = try await DeviceAttestation.shared.attestationChallengeUrl
            guard let url = makeAttestationURL(base: base, queryItems: [
                URLQueryItem(name: "s", value: sParam),
                URLQueryItem(name: "cid", value: clientId),
                URLQueryItem(name: "sys", value: "ios")
            ]) else {
                completion(.failure(.invalidResponse))
                return
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.timeoutInterval = 5.0

            let session = URLSession(configuration: .ephemeral)
            let task = session.dataTask(with: request) { data, response, error in
                guard error == nil,
                      let httpResponse = response as? HTTPURLResponse,
                      httpResponse.statusCode == 200,
                      let data = data else {
                    let code = (response as? HTTPURLResponse)?.statusCode ?? -1
                    let msg = String(data: data ?? Data(), encoding: .utf8) ?? error?.localizedDescription ?? "Unknown"
                    completion(.failure(.networkError(code, msg)))
                    return
                }

                do {
                    let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                    guard let challengeHash = json?["challengeHash"] as? String else {
                        completion(.failure(.serializationError))
                        return
                    }
                    completion(.success(challengeHash))
                } catch {
                    completion(.failure(.serializationError))
                }
            }
            task.resume()
        } catch let quickLink as DeviceAttestationError {
            completion(.failure(quickLink))
        } catch {
            completion(.failure(.configurationError(error.localizedDescription)))
        }
    }
}
