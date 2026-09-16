package com.azure.android.ai.vision.face.deviceattestation

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * Base64 + PEM ↔ Java key / certificate encoding helpers.
 *
 * Kept narrow on purpose: just enough to parse keys/certs delivered as PEM
 * strings on the wire and to round-trip DER bytes back through PEM.
 */
internal object KeyEncoding {

    private const val EC_ALGORITHM = "EC"

    private const val PEM_PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----"
    private const val PEM_PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----"
    private const val PEM_CERTIFICATE_HEADER = "-----BEGIN CERTIFICATE-----"
    private const val PEM_CERTIFICATE_FOOTER = "-----END CERTIFICATE-----"

    /**
     * Parses a P-256 public key from PEM or raw base64 input.
     * Accepts both `-----BEGIN PUBLIC KEY-----` envelopes and a bare X.509
     * SubjectPublicKeyInfo string.
     */
    fun parsePublicKey(keyData: String): PublicKey {
        try {
            val base64Key = stripPemEnvelope(keyData, PEM_PUBLIC_KEY_HEADER, PEM_PUBLIC_KEY_FOOTER)
            val keyBytes = base64Decode(base64Key)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
            return keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            throw Exception("Failed to parse public key: ${e.message}", e)
        }
    }

    /** URL-safe, no-wrap base64. */
    fun base64Encode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    /** Accepts both standard and URL-safe base64. */
    fun base64Decode(data: String): ByteArray {
        try {
            // Base64.DEFAULT only accepts the standard alphabet (+/) and
            // Base64.URL_SAFE only accepts the URL-safe alphabet (-_); neither
            // accepts both. Normalize the URL-safe characters to the standard
            // alphabet so input from either encoding decodes correctly.
            val normalized = data
                .replace('-', '+')
                .replace('_', '/')
            return Base64.decode(normalized, Base64.DEFAULT)
        } catch (e: Exception) {
            throw Exception("Failed to decode Base64 data: ${e.message}", e)
        }
    }

    /** Wraps DER cert bytes in `-----BEGIN CERTIFICATE-----` envelope. */
    fun derToPemCertificate(derBytes: ByteArray): String {
        val base64 = Base64.encodeToString(derBytes, Base64.DEFAULT)
        return "$PEM_CERTIFICATE_HEADER\n$base64$PEM_CERTIFICATE_FOOTER"
    }

    /**
     * Trims whitespace and removes PEM header/footer if present.
     * If [keyData] is already a bare base64 string, just trims whitespace.
     */
    private fun stripPemEnvelope(keyData: String, header: String, footer: String): String {
        val cleaned = keyData.trim()
        return if (cleaned.contains(header)) {
            cleaned
                .replace(header, "")
                .replace(footer, "")
                .replace("\\s".toRegex(), "")
        } else {
            cleaned.replace("\\s".toRegex(), "")
        }
    }
}
