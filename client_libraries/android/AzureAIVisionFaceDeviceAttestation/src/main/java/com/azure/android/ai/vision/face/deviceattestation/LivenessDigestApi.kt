package com.azure.android.ai.vision.face.deviceattestation

import org.json.JSONObject

/**
 * POST /api/liveness/digest — submits the attestation digest emitted at
 * the end of the liveness flow.
 *
 * Same Tink-blob envelope as [requestSessionToken]. Plaintext payload
 * carries `{ cid, os, digest }`; plaintext response carries
 * `{ success: Bool }`.
 */
internal suspend fun submitLivenessDigest(
    sParam: String,
    body: JSONObject
): AuthHttpClient.HttpResponse {
    val base = DeviceAttestationConfig.endpointUrl(DeviceAttestationConfig.endpoints.livenessDigest)
    val url = AuthHttpClient.buildUrl(base, "s" to sParam)
    return AuthHttpClient.postJson(url, body)
}
