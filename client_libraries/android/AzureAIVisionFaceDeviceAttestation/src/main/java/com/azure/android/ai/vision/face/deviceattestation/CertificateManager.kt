package com.azure.android.ai.vision.face.deviceattestation

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.security.auth.x500.X500Principal

/**
 * Manages hardware-backed P-256 *auth* certificates in Android KeyStore.
 *
 * - Keys are bound to the app signature
 * - StrongBox-backed when supported, falls back to TEE
 * - Used for ECDSA signing in the cert-based auth flow
 * - Cert auto-rotates when within [CERT_RENEWAL_THRESHOLD_DAYS] of expiry
 *
 * Ephemeral per-session encryption certs live in [EphemeralEncryptionCertManager].
 */
internal object CertificateManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "azure_vision_liveness_"
    private const val CERT_VALIDITY_DAYS = 10
    private const val CERT_RENEWAL_THRESHOLD_DAYS = 2

    // All KeyStore mutations go through this mutex so concurrent suspend
    // callers can't race against each other (e.g., parallel ensureKeyPair
    // calls from different coroutines).
    private val mutex = Mutex()

    // deviceUUID -> hex SHA-256 thumbprint of the cert. Computing the
    // thumbprint requires a KeyStore read + SHA-256 hash; caching avoids
    // doing this on every signed request.
    //
    // ConcurrentHashMap (vs. mutableMapOf) so the double-checked-locking read
    // in getCertificateThumbprint is actually safe: lockless reads have the
    // happens-before guarantee they need to see writes published from inside
    // mutex.withLock. With a plain HashMap, the un-locked read could land
    // mid-resize or see a partially-published entry.
    private val thumbprintCache = ConcurrentHashMap<String, String>()

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    // ---------------------------------------------------------------------
    // Key pair lifecycle
    // ---------------------------------------------------------------------

    /**
     * Returns a hardware-backed P-256 key pair for [deviceUUID], creating one
     * (or rotating an expiring one) as needed.
     *
     * On creation:
     * - StrongBox is tried first, falling back to TEE
     * - If [attestationChallenge] is provided (and SDK >= N), it's embedded in
     *   the attestation cert chain so the server can verify session freshness
     *
     * Renewal: if the existing cert expires within [CERT_RENEWAL_THRESHOLD_DAYS],
     * it's deleted and replaced.
     */
    suspend fun ensureKeyPair(
        deviceUUID: String,
        attestationChallenge: String? = null
    ): KeyPair = mutex.withLock {
        val alias = aliasFor(deviceUUID)

        if (keyStore.containsAlias(alias)) {
            val existing = keyStore.getCertificate(alias) as? X509Certificate
            if (existing != null) {
                if (existing.expiresSoon()) {
                    keyStore.deleteEntry(alias)
                    thumbprintCache.remove(deviceUUID)
                    // Fall through to generation below
                } else {
                    val privateKey = keyStore.getKey(alias, null) as PrivateKey
                    return KeyPair(existing.publicKey, privateKey)
                }
            }
        }

        val challengeBytes = attestationChallenge?.let(::hexToBytes)
        return try {
            generateKeyPair(alias, useStrongBox = true, attestationChallenge = challengeBytes)
        } catch (_: Exception) {
            // StrongBox unavailable on this device — TEE is the fallback path
            generateKeyPair(alias, useStrongBox = false, attestationChallenge = challengeBytes)
        }
    }

    /** True if [deviceUUID] has a cert in KeyStore. */
    suspend fun hasCertificate(deviceUUID: String): Boolean = mutex.withLock {
        keyStore.containsAlias(aliasFor(deviceUUID))
    }

    /** Deletes the cert + private key for [deviceUUID] and clears its cached thumbprint. */
    suspend fun deleteCertificate(deviceUUID: String) = mutex.withLock {
        val alias = aliasFor(deviceUUID)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
        thumbprintCache.remove(deviceUUID)
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    /** DER-encoded auth certificate bytes. Throws if none exists for [deviceUUID]. */
    suspend fun getCertificateDER(deviceUUID: String): ByteArray = mutex.withLock {
        val certificate = keyStore.getCertificate(aliasFor(deviceUUID))
            ?: throw IllegalStateException("Certificate not found for deviceUUID: $deviceUUID")
        certificate.encoded
    }

    /**
     * Hex-encoded SHA-256 thumbprint of the auth cert.
     * Cached after first computation per [deviceUUID].
     */
    suspend fun getCertificateThumbprint(deviceUUID: String): String {
        thumbprintCache[deviceUUID]?.let { return it }

        return mutex.withLock {
            // Double-check after acquiring the lock — another coroutine may
            // have populated it while we were waiting.
            thumbprintCache[deviceUUID]?.let { return it }

            val certificate = keyStore.getCertificate(aliasFor(deviceUUID))
                ?: throw IllegalStateException("Certificate not found for deviceUUID: $deviceUUID")

            val hash = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            val thumbprint = hash.joinToString("") { "%02x".format(it) }
            thumbprintCache[deviceUUID] = thumbprint
            thumbprint
        }
    }

    /** Returns the private key for signing/decryption. Throws if missing. */
    suspend fun getPrivateKey(deviceUUID: String): PrivateKey = mutex.withLock {
        val key = keyStore.getKey(aliasFor(deviceUUID), null)
            ?: throw IllegalStateException("Private key not found for deviceUUID: $deviceUUID")
        key as PrivateKey
    }

    /**
     * Attestation cert chain (leaf to root) proving hardware backing.
     * Returns null if [deviceUUID] has no cert.
     */
    suspend fun getAttestationCertificateChain(deviceUUID: String): Array<ByteArray>? = mutex.withLock {
        val chain = keyStore.getCertificateChain(aliasFor(deviceUUID)) ?: return@withLock null
        chain.map { it.encoded }.toTypedArray()
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private fun aliasFor(deviceUUID: String): String = "$KEY_ALIAS_PREFIX$deviceUUID"

    private fun X509Certificate.expiresSoon(): Boolean {
        val threshold = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, CERT_RENEWAL_THRESHOLD_DAYS)
        }.time
        return notAfter.before(threshold)
    }

    private fun generateKeyPair(
        alias: String,
        useStrongBox: Boolean,
        attestationChallenge: ByteArray? = null
    ): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )

        // Backdate notBefore by 2 hours — production CAs do the same so a
        // freshly-minted cert isn't briefly "not yet valid" on relying parties
        // whose clocks lag the device's. 2 hours covers timezone-misconfigured
        // devices, server clock drift, and any delay between generation and
        // the cert hitting the server.
        val startDate = Calendar.getInstance().apply { add(Calendar.MINUTE, -120) }
        val endDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, CERT_VALIDITY_DAYS) }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN  // ECC doesn't support PURPOSE_DECRYPT directly
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setCertificateSubject(X500Principal("CN=Azure Vision Liveness, O=Microsoft"))
            .setCertificateNotBefore(startDate.time)
            .setCertificateNotAfter(endDate.time)
            .setUserAuthenticationRequired(false)
            .apply {
                if (useStrongBox) {
                    setIsStrongBoxBacked(true)
                }
                // Key attestation requires SDK 24+; the challenge embeds in the
                // attestation cert chain so the server can verify freshness.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && attestationChallenge != null) {
                    setAttestationChallenge(attestationChallenge)
                }
            }
            .build()

        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    /**
     * Decodes a hex string into its raw byte representation. Validates input
     * upfront — odd length or non-hex characters would otherwise either crash
     * with `IndexOutOfBoundsException` (odd length) or silently produce
     * garbage bytes (`Character.digit` returns -1 for invalid chars), and
     * the garbage would then flow into `KeyGenParameterSpec.setAttestationChallenge`
     * with no clue at the call site that the server's challenge was malformed.
     */
    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) {
            "Hex string must have even length, got ${hex.length}"
        }
        val data = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            val hi = Character.digit(hex[i], 16)
            val lo = Character.digit(hex[i + 1], 16)
            require(hi >= 0 && lo >= 0) {
                "Invalid hex character at index $i: '${hex[i]}${hex[i + 1]}'"
            }
            data[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return data
    }
}
