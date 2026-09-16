package com.azure.android.ai.vision.face.deviceattestation

import org.json.JSONObject

/**
 * POST /api/attestation/verify — does the server have a record of this
 * auth cert?
 *
 * Request body (signed envelope):
 * ```
 * {
 *   "payload":        "{\"challengeHash\":\"...\",\"encryptionPublicCert\":\"...\"}",
 *   "authPublicCert": "-----BEGIN CERTIFICATE-----\n...",
 *   "signature":      "<base64 ECDSA over payload bytes>"
 * }
 * ```
 *
 * Response (plain JSON):
 *   `{ "exists": Bool, "serverEncryptionPublicKey": String? }`.
 */
internal suspend fun verifyAttestation(
    sParam: String,
    clientId: String,
    body: JSONObject
): AuthHttpClient.HttpResponse {
    val base = DeviceAttestationConfig.endpointUrl(DeviceAttestationConfig.endpoints.attestationVerify)
    val url = AuthHttpClient.buildUrl(base, "s" to sParam, "cid" to clientId, "sys" to AuthHttpClient.SYSTEM)
    return AuthHttpClient.postJson(url, body)
}
