package com.azure.android.ai.vision.face.deviceattestation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Calendar
import java.util.UUID
import javax.security.auth.x500.X500Principal

/**
 * Manages short-lived ephemeral P-256 encryption certs used for per-session
 * encrypted communication with the server.
 *
 * These keys are generated in AndroidKeyStore for hardware backing but are NOT
 * the long-lived auth cert managed by [CertificateManager]. They live for one
 * session only — the caller is responsible for calling [deleteCert] once the
 * session is done so the temporary KeyStore alias doesn't leak.
 */
internal object EphemeralEncryptionCertManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val ALIAS_PREFIX = "ephemeral_encryption_"
    private const val VALIDITY_DAYS = 1

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    /**
     * Bundle returned from [generateCert]: the PEM cert (handed to the server)
     * and the matching private key (kept locally for later decryption).
     */
    data class EphemeralEncryptionCert(
        val certificatePEM: String,
        val privateKey: PrivateKey
    )

    /**
     * Generates a new ephemeral P-256 key pair with a self-signed cert valid
     * for [VALIDITY_DAYS]. The cert is returned as PEM for transport to the
     * server; the private key reference stays in KeyStore until [deleteCert]
     * is called.
     */
    fun generateCert(): EphemeralEncryptionCert {
        val tempAlias = "$ALIAS_PREFIX${UUID.randomUUID()}"

        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )

            // Backdate notBefore by 2 hours for the same clock-skew reasons as
            // the auth cert in CertificateManager — the server rejects a cert
            // whose notBefore is in the future, and freshly-minted certs hit
            // that case when device and server clocks aren't aligned.
            val startDate = Calendar.getInstance().apply { add(Calendar.MINUTE, -120) }
            val endDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, VALIDITY_DAYS) }

            val spec = KeyGenParameterSpec.Builder(
                tempAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_AGREE_KEY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(X500Principal("CN=Ephemeral Encryption, O=Azure Vision Liveness"))
                .setCertificateNotBefore(startDate.time)
                .setCertificateNotAfter(endDate.time)
                .setUserAuthenticationRequired(false)
                .setIsStrongBoxBacked(false) // StrongBox isn't required for ephemeral keys
                .build()

            kpg.initialize(spec)
            kpg.generateKeyPair()

            val certificate = keyStore.getCertificate(tempAlias) as X509Certificate
            val privateKey = keyStore.getKey(tempAlias, null) as PrivateKey
            val certPEM = CryptoHelper.derToPemCertificate(certificate.encoded)

            return EphemeralEncryptionCert(certPEM, privateKey)
        } catch (e: Exception) {
            // Best-effort cleanup so we don't leave a half-initialized alias behind
            try {
                if (keyStore.containsAlias(tempAlias)) {
                    keyStore.deleteEntry(tempAlias)
                }
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
            throw Exception("Failed to generate ephemeral encryption certificate: ${e.message}", e)
        }
    }

    /**
     * Deletes the ephemeral cert backing [privateKey] from KeyStore.
     * Safe to call multiple times; logs and swallows failures.
     */
    fun deleteCert(privateKey: PrivateKey) {
        try {
            val alias = findAliasForKey(privateKey)
            if (alias != null && keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (e: Exception) {
            // Non-fatal — log but don't propagate; cleanup failures shouldn't
            // tear down the calling session.
            println("Warning: Failed to delete ephemeral encryption cert: ${e.message}")
        }
    }

    /**
     * Looks up the KeyStore alias whose stored key equals [privateKey].
     * Used only for cleanup; returns null if the key isn't in KeyStore.
     */
    private fun findAliasForKey(privateKey: PrivateKey): String? {
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            try {
                if (keyStore.getKey(alias, null) == privateKey) {
                    return alias
                }
            } catch (_: Exception) {
                // Continue searching
            }
        }
        return null
    }
}
