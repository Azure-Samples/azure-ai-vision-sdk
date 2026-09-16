package com.azure.android.ai.vision.face.deviceattestation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection.HTTP_OK
import java.security.PrivateKey

/**
 * Glue for the attestation half of the QuickLink auth flow. Factored out of
 * [DeviceAttestation] so each step stays small enough to review on its own:
 *
 *   1. fetch the server challenge          → [fetchChallenge]   (/attestation/challenge)
 *   2. obtain server-side session keys     → [obtainSessionKeys]
 *        a. ask whether server knows cert  → [verifyAttestation] (/attestation/verify)
 *        b. if not, attest + register      → [registerAttestation] (/attestation/register)
 *
 * Result types ([ChallengeResult], [SessionKeysResult]) are internal — callers
 * outside this file consume only the public [DeviceAttestation.StartSessionResult].
 */
internal object AttestationFlow {

    private const val SYSTEM = "android"

    // ---------------------------------------------------------------------
    // Step 1: challenge fetch
    // ---------------------------------------------------------------------

    sealed class ChallengeResult {
        data class Success(val challengeHash: String) : ChallengeResult()
        data class Failure(val code: Int, val message: String) : ChallengeResult()
    }

    /**
     * POST /attestation/challenge, parse the JSON, defensively cross-check
     * that the server echoed back the clientId / system we sent.
     */
    suspend fun fetchChallenge(sParam: String, deviceUUID: String): ChallengeResult {
        val response = fetchAttestationChallenge(sParam, deviceUUID)
        if (response.code != HTTP_OK || response.body == null) {
            return ChallengeResult.Failure(response.code, response.body ?: "Failed to get challenge JSON")
        }

        val json = JSONObject(response.body)
        val challengeHash = json.getString("challengeHash")

        val responseClientId = json.optString("clientId", "")
        val responseSystem = json.optString("system", "")
        if (responseClientId.isNotEmpty() && responseClientId != deviceUUID) {
            return ChallengeResult.Failure(-1,
                "Server response clientId mismatch: expected $deviceUUID, got $responseClientId")
        }
        if (responseSystem.isNotEmpty() && responseSystem != SYSTEM) {
            return ChallengeResult.Failure(-1,
                "Server response system mismatch: expected $SYSTEM, got $responseSystem")
        }

        return ChallengeResult.Success(challengeHash)
    }

    // ---------------------------------------------------------------------
    // Step 2: obtain session keys (verify-or-register)
    // ---------------------------------------------------------------------

    /**
     * Internal result for [obtainSessionKeys]: either success with the two keys
     * handed to the returned [AttestationSession], or a typed failure the caller
     * propagates via [Failure.toStartSessionResult].
     */
    sealed class SessionKeysResult {
        data class Success(
            val serverPublicKeyPem: String,
            val ephemeralPrivateKey: PrivateKey
        ) : SessionKeysResult()

        data class Failure(
            val kind: Kind,
            val code: Int = -1,
            val message: String = "",
            val exception: Throwable? = null
        ) : SessionKeysResult() {
            enum class Kind { Error, Exception }

            fun toStartSessionResult(): DeviceAttestation.StartSessionResult = when (kind) {
                Kind.Error -> DeviceAttestation.StartSessionResult.Error(code, message)
                Kind.Exception -> DeviceAttestation.StartSessionResult.Exception(
                    exception ?: RuntimeException(message)
                )
            }
        }

        companion object {
            fun error(code: Int, message: String) = Failure(Failure.Kind.Error, code, message)
            fun exception(e: Throwable) = Failure(Failure.Kind.Exception, exception = e)
        }
    }

    /**
     * Returns (server encryption pubkey PEM, ephemeral encryption privkey)
     * either by [verifyKnownAttestation] (cert already known to server) or
     * by [registerNewAttestation] (first time / after server-side TTL).
     */
    suspend fun obtainSessionKeys(
        context: Context,
        sParam: String,
        deviceUUID: String,
        challengeHash: String,
        keyExisted: Boolean
    ): SessionKeysResult {
        when (val check = verifyKnownAttestation(sParam, deviceUUID, challengeHash)) {
            is VerifyResult.Success -> {
                if (check.certExists) {
                    val serverKey = check.serverEncryptionPublicKey
                        ?: return SessionKeysResult.error(-1,
                            "Server encryption public key not returned for existing certificate")
                    val ephemeralKey = check.ephemeralEncryptionPrivateKey
                        ?: return SessionKeysResult.error(-1,
                            "Ephemeral encryption private key not returned for existing certificate")
                    return SessionKeysResult.Success(serverKey, ephemeralKey)
                }

                // Server has no record of our cert — register it now.
                // If a local StrongBox cert already exists, rotate it so the
                // new attestation cert chain carries the *current* session's
                // challengeHash in its keymaster attestation extension (OID
                // 1.3.6.1.4.1.11129.2.1.17), which the server enforces. Skip
                // when keyExisted=false — ensureKeyPair already bound
                // challengeHash at first-time generation.
                if (keyExisted) {
                    CertificateManager.deleteCertificate(deviceUUID)
                    CertificateManager.ensureKeyPair(deviceUUID, attestationChallenge = challengeHash)
                }

                return when (val reg = registerNewAttestation(context, sParam, deviceUUID, challengeHash)) {
                    is RegisterResult.Success ->
                        SessionKeysResult.Success(reg.serverEncryptionPublicKey, reg.ephemeralEncryptionPrivateKey)
                    is RegisterResult.Error -> SessionKeysResult.error(reg.code, reg.message)
                    is RegisterResult.Exception -> SessionKeysResult.exception(reg.exception)
                }
            }
            is VerifyResult.Error -> return SessionKeysResult.error(check.code, check.message)
            is VerifyResult.Exception -> return SessionKeysResult.exception(check.exception)
        }
    }

    // ---------------------------------------------------------------------
    // Step 2a: /attestation/verify
    // ---------------------------------------------------------------------

    private sealed class VerifyResult {
        data class Success(
            val certExists: Boolean,
            val serverEncryptionPublicKey: String?,
            val ephemeralEncryptionPrivateKey: PrivateKey?
        ) : VerifyResult()
        data class Error(val code: Int, val message: String) : VerifyResult()
        data class Exception(val exception: Throwable) : VerifyResult()
    }

    /**
     * Body shape: `{ payload: "<json>", authPublicCert: "<pem>", signature: "<b64>" }`
     * where payload = `{ challengeHash, encryptionPublicCert }`.
     */
    private suspend fun verifyKnownAttestation(
        sParam: String,
        deviceUUID: String,
        challengeHash: String
    ): VerifyResult = withContext(Dispatchers.IO) {
        var ephemeralCert: EphemeralEncryptionCertManager.EphemeralEncryptionCert? = null
        try {
            ephemeralCert = EphemeralEncryptionCertManager.generateCert()
            val body = buildSignedAuthBody(
                deviceUUID = deviceUUID,
                payload = JSONObject().apply {
                    put("challengeHash", challengeHash)
                    put("encryptionPublicCert", ephemeralCert.certificatePEM)
                }
            )

            val response = verifyAttestation(sParam, deviceUUID, body)
            if (response.code != HTTP_OK) {
                EphemeralEncryptionCertManager.deleteCert(ephemeralCert.privateKey)
                return@withContext VerifyResult.Error(response.code, response.body ?: "Unknown error")
            }

            val json = JSONObject(response.body!!)
            val exists = json.getBoolean("exists")
            if (exists) {
                VerifyResult.Success(true, json.getString("serverEncryptionPublicKey"), ephemeralCert.privateKey)
            } else {
                // No registration coming this round, so the ephemeral cert is dead weight.
                EphemeralEncryptionCertManager.deleteCert(ephemeralCert.privateKey)
                VerifyResult.Success(false, null, null)
            }
        } catch (e: Exception) {
            ephemeralCert?.let { EphemeralEncryptionCertManager.deleteCert(it.privateKey) }
            VerifyResult.Exception(e)
        }
    }

    // ---------------------------------------------------------------------
    // Step 2b: /attestation/register (attest + register)
    // ---------------------------------------------------------------------

    private sealed class RegisterResult {
        data class Success(
            val serverEncryptionPublicKey: String,
            val ephemeralEncryptionPrivateKey: PrivateKey
        ) : RegisterResult()
        data class Error(val code: Int, val message: String) : RegisterResult()
        data class Exception(val exception: Throwable) : RegisterResult()
    }

    /**
     * Body shape: same as [verifyKnownAttestation] but the inner payload
     * also carries `attestJson = { token, certificateChain[] }`.
     */
    private suspend fun registerNewAttestation(
        context: Context,
        sParam: String,
        deviceUUID: String,
        challengeHash: String
    ): RegisterResult = withContext(Dispatchers.IO) {
        var ephemeralCert: EphemeralEncryptionCertManager.EphemeralEncryptionCert? = null
        try {
            // Bind the integrity token to the StrongBox auth key via its cert
            // thumbprint. Session-freshness is already in the keymaster
            // extension on the chain, so the integrity nonce only needs to
            // bind the key — pass the cert thumbprint directly. (Hashing the
            // hex thumbprint again would not add security: the inner SHA-256
            // already gives 256-bit entropy.)
            val integrityRequestHash = CertificateManager.getCertificateThumbprint(deviceUUID)
            val integrityToken = when (val r = PlayIntegrityTokenProvider.requestToken(context, integrityRequestHash)) {
                is PlayIntegrityTokenProvider.TokenResult.Success -> r.token
                is PlayIntegrityTokenProvider.TokenResult.Exception ->
                    return@withContext RegisterResult.Exception(r.exception)
            }

            ephemeralCert = EphemeralEncryptionCertManager.generateCert()
            val attestJson = buildAttestJson(deviceUUID, integrityToken).toString()
            val body = buildSignedAuthBody(
                deviceUUID = deviceUUID,
                payload = JSONObject().apply {
                    put("challengeHash", challengeHash)
                    put("encryptionPublicCert", ephemeralCert.certificatePEM)
                    put("attestJson", attestJson)
                }
            )

            val response = registerAttestation(sParam, deviceUUID, body)
            if (response.code != HTTP_OK) {
                EphemeralEncryptionCertManager.deleteCert(ephemeralCert.privateKey)
                return@withContext RegisterResult.Error(response.code, response.body ?: "Unknown error")
            }

            val json = JSONObject(response.body!!)
            RegisterResult.Success(
                json.getString("serverEncryptionPublicKey"),
                ephemeralCert.privateKey
            )
        } catch (e: Exception) {
            ephemeralCert?.let { EphemeralEncryptionCertManager.deleteCert(it.privateKey) }
            RegisterResult.Exception(e)
        }
    }

    /**
     * Bundles the Play Integrity token with the StrongBox attestation cert
     * chain. The server verifies both: token proves "this is a real device",
     * chain proves "this key is hardware-backed".
     */
    private suspend fun buildAttestJson(deviceUUID: String, integrityToken: String): JSONObject {
        val attestationChain = CertificateManager.getAttestationCertificateChain(deviceUUID)
        return JSONObject().apply {
            put("token", integrityToken)
            if (attestationChain != null) {
                val chainBase64 = JSONArray().apply {
                    attestationChain.forEach { put(CryptoHelper.base64Encode(it)) }
                }
                put("certificateChain", chainBase64)
            }
        }
    }

    /**
     * Wraps [payload] as a signed envelope:
     *   `{ payload: "<json>", authPublicCert: "<pem>", signature: "<b64 ecdsa>" }`
     * Signature is over the UTF-8 bytes of the serialized payload string.
     */
    private suspend fun buildSignedAuthBody(
        deviceUUID: String,
        payload: JSONObject
    ): JSONObject {
        val payloadString = payload.toString()
        val authCertPEM = CryptoHelper.derToPemCertificate(CertificateManager.getCertificateDER(deviceUUID))
        val authPrivateKey = CertificateManager.getPrivateKey(deviceUUID)
        val signature = CryptoHelper.signData(payloadString.toByteArray(Charsets.UTF_8), authPrivateKey)

        return JSONObject().apply {
            put("payload", payloadString)
            put("authPublicCert", authCertPEM)
            put("signature", CryptoHelper.base64Encode(signature))
        }
    }
}
