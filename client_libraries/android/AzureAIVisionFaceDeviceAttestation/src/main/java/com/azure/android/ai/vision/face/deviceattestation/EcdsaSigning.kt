package com.azure.android.ai.vision.face.deviceattestation

import java.security.PrivateKey
import java.security.Signature

/**
 * ECDSA-SHA256 sign, factored out of [CryptoHelper] so the algorithm choice
 * lives in one place.
 */
internal object EcdsaSigning {

    private const val ECDSA_SIGNATURE = "SHA256withECDSA"

    /** Signs [data] with [privateKey]. Returns the raw DER-encoded signature bytes. */
    fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray {
        try {
            val signature = Signature.getInstance(ECDSA_SIGNATURE)
            signature.initSign(privateKey)
            signature.update(data)
            return signature.sign()
        } catch (e: Exception) {
            throw Exception("Failed to sign data: ${e.message}", e)
        }
    }
}
