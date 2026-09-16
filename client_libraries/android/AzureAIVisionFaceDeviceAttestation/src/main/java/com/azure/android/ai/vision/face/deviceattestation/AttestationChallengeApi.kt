package com.azure.android.ai.vision.face.deviceattestation

/**
 * POST /api/attestation/challenge — kicks off a session and returns the
 * server challenge JSON. All downstream calls bind this `challengeHash`
 * into their attestation + ECDSA signatures so the server can prove
 * session freshness.
 *
 * Response body is returned verbatim; the caller parses `challengeHash`
 * (and cross-checks the echoed `clientId` / `system`).
 */
internal suspend fun fetchAttestationChallenge(
    sParam: String,
    clientId: String
): AuthHttpClient.HttpResponse {
    val base = DeviceAttestationConfig.endpointUrl(DeviceAttestationConfig.endpoints.attestationChallenge)
    val url = AuthHttpClient.buildUrl(base, "s" to sParam, "cid" to clientId, "sys" to AuthHttpClient.SYSTEM)
    return AuthHttpClient.postEmpty(url)
}
