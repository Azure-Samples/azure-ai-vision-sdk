package com.azure.android.ai.vision.face.deviceattestation

import org.json.JSONObject

/**
 * POST /api/attestation/register — registers a new auth cert + Play
 * Integrity token + Key Attestation chain with the server.
 *
 * Request body shape matches [verifyAttestation]'s envelope, plus an
 * `attestJson` field inside the payload:
 *
 * ```
 * payload.attestJson = "{ \"token\": <play-integrity JWT>,
 *                         \"certificateChain\": [<base64 DER>, ...] }"
 * ```
 *
 * Response: `{ "message": String, "serverEncryptionPublicKey": String }`.
 */
internal suspend fun registerAttestation(
    sParam: String,
    clientId: String,
    body: JSONObject
): AuthHttpClient.HttpResponse {
    val base = DeviceAttestationConfig.endpointUrl(DeviceAttestationConfig.endpoints.attestationRegister)
    val url = AuthHttpClient.buildUrl(base, "s" to sParam, "cid" to clientId, "sys" to AuthHttpClient.SYSTEM)
    return AuthHttpClient.postJson(url, body)
}
