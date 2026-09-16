package com.azure.android.ai.vision.face.deviceattestation

import org.json.JSONObject
import java.security.PrivateKey
import java.security.PublicKey

/**
 * The encrypt-and-sign envelope used by `/session/token` and `/liveness/digest`.
 *
 *   request  = { encryptedData: <Tink blob>, signature: <ECDSA over Tink blob> }
 *   response = { encryptedData: <Tink blob> }
 *
 * Encryption uses ECIES (HKDF + AES-GCM) bound to the server's encryption
 * public key. Signing uses the device's StrongBox auth private key so the
 * server can prove the request really comes from the registered cert.
 * Responses are encrypted to the client's ephemeral encryption public key,
 * so only the matching ephemeral private key can read them.
 */
internal object EncryptedPayloadEnvelope {

    /**
     * Wraps [payload] for transport:
     * 1. Encrypt the payload bytes with [serverPubKey] (Tink blob, base64).
     * 2. Sign the blob's UTF-8 bytes with [authPrivKey].
     */
    fun build(
        payload: JSONObject,
        serverPubKey: PublicKey,
        authPrivKey: PrivateKey
    ): JSONObject {
        val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)
        val encrypted = CryptoHelper.encryptToTinkBlob(payloadBytes, serverPubKey)
        val signature = CryptoHelper.signData(encrypted.toByteArray(Charsets.UTF_8), authPrivKey)
        return JSONObject().apply {
            put("encryptedData", encrypted)
            put("signature", CryptoHelper.base64Encode(signature))
        }
    }

    /**
     * Extracts the `encryptedData` blob from [responseBody] and decrypts it
     * with [ephemeralPrivKey], returning UTF-8 plaintext.
     */
    fun decryptBody(responseBody: String, ephemeralPrivKey: PrivateKey): String {
        val encryptedData = JSONObject(responseBody).getString("encryptedData")
        val decrypted = CryptoHelper.decryptFromTinkBlob(encryptedData, ephemeralPrivKey)
        return String(decrypted, Charsets.UTF_8)
    }

    /** Same as [decryptBody] but also parses out the `token` field. */
    fun decryptToken(responseBody: String, ephemeralPrivKey: PrivateKey): String {
        return JSONObject(decryptBody(responseBody, ephemeralPrivKey)).getString("token")
    }
}
