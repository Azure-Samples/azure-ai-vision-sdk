package com.azure.android.ai.vision.face.deviceattestation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection.HTTP_OK
import java.security.PrivateKey
import java.security.PublicKey

/**
 * A single attestation session, created by [DeviceAttestation.startSession].
 *
 * Owns the per-session secrets — the server [challengeHash], the server
 * encryption public key, and this client's ephemeral encryption private key —
 * privately, so they never leak through the public API. Reuse the same instance
 * for the token exchange and the final liveness digest; the ephemeral key is
 * wiped automatically once [submitLivenessDigest] completes (and whenever a new
 * session replaces this one), so callers never need to release it themselves.
 *
 * Instances are created only by the library; callers obtain one from
 * [DeviceAttestation.startSession] or re-resolve it via [DeviceAttestation.currentSession].
 */
class AttestationSession internal constructor(
    /** Server session id (the App Link `s` parameter). */
    val sessionId: String,
    /** Stable per-device correlation id. */
    val deviceId: String,
    private val challengeHash: String,
    private val serverEncryptionPublicKey: PublicKey,
    private val ephemeralEncryptionPrivateKey: PrivateKey,
) {

    /** Result of [fetchSessionToken]. */
    sealed class SessionTokenResult {
        data class Success(val token: String) : SessionTokenResult()
        data class Error(val code: Int, val message: String) : SessionTokenResult()
        data class Exception(val exception: Throwable) : SessionTokenResult()
    }

    /** Result of [submitLivenessDigest]. */
    sealed class LivenessDigestResult {
        object Success : LivenessDigestResult()
        data class Error(val code: Int, val message: String) : LivenessDigestResult()
        data class Exception(val exception: Throwable) : LivenessDigestResult()
    }

    /**
     * Encrypt + sign the session-token request and decrypt the server's
     * encrypted response. Server authenticates the call via the auth-cert
     * signature; no integrity token here. Uses this session's challengeHash
     * and keys internally.
     */
    suspend fun fetchSessionToken(): SessionTokenResult = withContext(Dispatchers.IO) {
        try {
            val authPrivKey = CertificateManager.getPrivateKey(deviceId)

            val payload = JSONObject().apply {
                put("challengeHash", challengeHash)
                put("clientId", deviceId)
                put("system", SYSTEM)
            }
            val requestBody = EncryptedPayloadEnvelope.build(payload, serverEncryptionPublicKey, authPrivKey)
            val response = requestSessionToken(sessionId, requestBody)

            if (response.code != HTTP_OK) {
                return@withContext SessionTokenResult.Error(response.code, response.body ?: "Unknown error")
            }

            val token = EncryptedPayloadEnvelope.decryptToken(response.body!!, ephemeralEncryptionPrivateKey)
            SessionTokenResult.Success(token)
        } catch (e: Exception) {
            SessionTokenResult.Exception(e)
        }
    }

    /**
     * Submits the attestation digest emitted at the end of the liveness flow.
     * Same envelope as [fetchSessionToken] (encrypt + sign + decrypt).
     *
     * This is the final call in the flow: once it returns (success or failure),
     * the ephemeral key is wiped and the session is released automatically.
     */
    suspend fun submitLivenessDigest(digest: String): LivenessDigestResult = withContext(Dispatchers.IO) {
        if (digest.isEmpty()) return@withContext LivenessDigestResult.Error(-1, "digest is empty")

        try {
            val authPrivKey = CertificateManager.getPrivateKey(deviceId)

            val payload = JSONObject().apply {
                put("cid", deviceId)
                put("os", SYSTEM)
                put("digest", digest)
            }
            val requestBody = EncryptedPayloadEnvelope.build(payload, serverEncryptionPublicKey, authPrivKey)
            val response = submitLivenessDigest(sessionId, requestBody)

            if (response.code != HTTP_OK) {
                return@withContext LivenessDigestResult.Error(response.code, response.body ?: "Unknown error")
            }

            val decrypted = EncryptedPayloadEnvelope.decryptBody(response.body!!, ephemeralEncryptionPrivateKey)
            val success = JSONObject(decrypted).optBoolean("success", false)
            if (success) LivenessDigestResult.Success
            else LivenessDigestResult.Error(-1, "Liveness digest post returned success=false")
        } catch (e: Exception) {
            LivenessDigestResult.Exception(e)
        } finally {
            // Digest is the last call in the flow — wipe the ephemeral key and
            // release this session so callers don't have to.
            cleanup()
        }
    }

    /**
     * Wipes this session's ephemeral encryption key from the KeyStore and clears
     * it as [DeviceAttestation]'s active session. Invoked automatically after the
     * digest and when a new session replaces this one; idempotent.
     */
    internal fun cleanup() {
        EphemeralEncryptionCertManager.deleteCert(ephemeralEncryptionPrivateKey)
        DeviceAttestation.clearSession(this)
    }

    private companion object {
        const val SYSTEM = "android"
    }
}
