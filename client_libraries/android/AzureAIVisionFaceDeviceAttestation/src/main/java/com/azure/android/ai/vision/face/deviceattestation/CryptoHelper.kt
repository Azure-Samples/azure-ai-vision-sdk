package com.azure.android.ai.vision.face.deviceattestation

import java.security.PrivateKey
import java.security.PublicKey

/**
 * Public crypto API used across the auth flow. Each method delegates to a
 * single-concern helper so the algorithm choices live in one place:
 *
 *   ECIES (encrypt/decrypt) → [EciesCrypto]    (Tink subtle, P-256 + HKDF + AES-256-GCM)
 *   ECDSA signing           → [EcdsaSigning]   (SHA256withECDSA)
 *   PEM / base64 / DER      → [KeyEncoding]
 */
internal object CryptoHelper {

    /** ECIES-encrypts [data] and returns the Tink-format base64 blob. */
    fun encryptToTinkBlob(data: ByteArray, publicKey: PublicKey): String =
        KeyEncoding.base64Encode(EciesCrypto.encrypt(data, publicKey))

    /** Decrypts a base64 Tink blob back to plaintext. */
    fun decryptFromTinkBlob(tinkBlob: String, privateKey: PrivateKey): ByteArray =
        EciesCrypto.decrypt(KeyEncoding.base64Decode(tinkBlob), privateKey)

    /** ECDSA-SHA256 sign over [data]. */
    fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray =
        EcdsaSigning.sign(data, privateKey)

    /** Parses a P-256 public key from PEM or raw base64 input. */
    fun parsePublicKey(keyData: String): PublicKey = KeyEncoding.parsePublicKey(keyData)

    /** URL-safe, no-wrap base64. */
    fun base64Encode(data: ByteArray): String = KeyEncoding.base64Encode(data)

    /** Accepts both standard and URL-safe base64. */
    fun base64Decode(data: String): ByteArray = KeyEncoding.base64Decode(data)

    /** Wraps DER cert bytes in a `-----BEGIN CERTIFICATE-----` envelope. */
    fun derToPemCertificate(derBytes: ByteArray): String = KeyEncoding.derToPemCertificate(derBytes)
}
