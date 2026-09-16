package com.azure.android.ai.vision.face.deviceattestation

import org.json.JSONObject

/**
 * POST /api/session/token — exchanges a signed encrypted payload for a
 * session token.
 *
 * Request body (Tink-blob envelope):
 * ```
 * {
 *   "encryptedData": "<base64 Tink blob>",
 *   "signature":     "<base64 ECDSA over the Tink blob>"
 * }
 * ```
 *
 * Response: `{ "encryptedData": "<base64 Tink blob>" }` — caller decrypts
 * to `{ "token": String }`.
 */
internal suspend fun requestSessionToken(
    sParam: String,
    body: JSONObject
): AuthHttpClient.HttpResponse {
    val base = DeviceAttestationConfig.endpointUrl(DeviceAttestationConfig.endpoints.sessionToken)
    val url = AuthHttpClient.buildUrl(base, "s" to sParam)
    return AuthHttpClient.postJson(url, body)
}
