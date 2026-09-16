package com.azure.android.ai.vision.face.deviceattestation

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.EllipticCurves
import com.google.crypto.tink.subtle.Hkdf
import java.security.KeyPairGenerator
import java.security.NoSuchProviderException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

/**
 * ECIES-AEAD-HKDF (P-256 + ECDH + HKDF-SHA256 + AES-256-GCM) — the Tink wire
 * format used to exchange encrypted payloads with the server.
 *
 * Wire layout (concatenated, then base64-encoded by the caller):
 *
 *   point(65) || iv(12) || ciphertext || authTag(16)
 *
 * ECDH composes Tink's subtle primitives inline rather than calling
 * `EllipticCurves.computeSharedSecret`. The Tink helper routes through its
 * own preferred-provider list (Conscrypt first), which refuses
 * AndroidKeyStore-resident keys — only the AndroidKeyStore provider can use
 * them. We pick the JCA provider per-key (see [keyAgreementFor]) so the
 * AndroidKeyStore-resident decrypt path always lands on the right provider,
 * regardless of the default lookup order.
 *
 * Note on the high-level API: Tink 1.13+ made `EciesAeadHkdfHybridEncrypt`'s
 * `ECPublicKey` constructor private. The public path through `KeysetHandle`
 * needs `EciesPrivateKey.createForNistCurve(scalar)`, and KeyStore-backed
 * keys don't expose the scalar. So we compose Tink's own subtle primitives
 * (`Hkdf`, `AesGcmJce`, `pointEncode`/`pointDecode`) inline.
 */
internal object EciesCrypto {

    private const val EC_ALGORITHM = "EC"

    /** Encrypts [data] for [recipientPublicKey]. Returns Tink wire bytes. */
    fun encrypt(data: ByteArray, recipientPublicKey: PublicKey): ByteArray {
        try {
            // 1. One-shot ephemeral P-256 keypair (stack-only).
            val kpg = KeyPairGenerator.getInstance(EC_ALGORITHM)
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val ephemeralKeyPair = kpg.generateKeyPair()

            // 2. Encode ephemeral public key as uncompressed point (0x04 || X || Y).
            val ephemeralPoint = EllipticCurves.pointEncode(
                EllipticCurves.CurveType.NIST_P256,
                EllipticCurves.PointFormatType.UNCOMPRESSED,
                (ephemeralKeyPair.public as ECPublicKey).w
            )

            // 3. ECDH(ephemeral_priv, recipient_pub) → shared secret.
            val sharedSecret = deriveSharedSecret(ephemeralKeyPair.private, recipientPublicKey)

            // 4. AES-256 key = HKDF(SHA-256, ikm = point || sharedSecret, empty salt + info).
            //    Shoup-style construction; must match Tink (server) and iOS.
            val aesKey = deriveAesKey(ephemeralPoint, sharedSecret)

            // 5. AES-GCM seal — output is iv(12) || ciphertext || authTag(16).
            val aesCiphertext = AesGcmJce(aesKey).encrypt(data, byteArrayOf())

            // 6. Wire layout.
            return ephemeralPoint + aesCiphertext
        } catch (e: Exception) {
            throw Exception("Failed to encrypt data with public key: ${e.message}", e)
        }
    }

    /** Decrypts Tink wire bytes back to plaintext. */
    fun decrypt(ciphertext: ByteArray, privateKey: PrivateKey): ByteArray {
        try {
            require(ciphertext.size >= 65 + 12 + 16) { "Ciphertext too short: ${ciphertext.size} bytes" }
            val pointBytes = ciphertext.sliceArray(0 until 65)
            require(pointBytes[0] == 0x04.toByte()) { "Invalid ephemeral public key point format" }
            val aesCiphertext = ciphertext.sliceArray(65 until ciphertext.size)

            val peerPublicKey = EllipticCurves.getEcPublicKey(
                EllipticCurves.CurveType.NIST_P256,
                EllipticCurves.PointFormatType.UNCOMPRESSED,
                pointBytes
            )
            val sharedSecret = deriveSharedSecret(privateKey, peerPublicKey)
            val aesKey = deriveAesKey(pointBytes, sharedSecret)

            return AesGcmJce(aesKey).decrypt(aesCiphertext, byteArrayOf())
        } catch (e: Exception) {
            throw Exception("Failed to decrypt data with private key: ${e.message}", e)
        }
    }

    /** ECDH — provider picked per-key. See [keyAgreementFor]. */
    private fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = keyAgreementFor(privateKey)
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * AndroidKeyStore-resident private keys expose `getFormat() == null`
     * because they're non-extractable. `KeyAgreement.getInstance("ECDH")`
     * picks ONE provider at instantiation — typically Conscrypt — and there
     * is no auto-routing to the provider that owns the key. Conscrypt's
     * `OpenSSLECDHKeyAgreement.engineInit` then fails the format check with
     * `InvalidKeyException: Key encoding is null`.
     *
     * So: when the key looks AndroidKeyStore-resident, ask the AndroidKeyStore
     * provider directly. Software-resident keys (the encrypt-side ephemeral
     * minted by `KeyPairGenerator`) keep the default lookup, which is fine
     * since their scalar is extractable and any provider can use them.
     *
     * The `NoSuchProviderException` fallback covers test environments and
     * future Android variants where the provider isn't registered.
     */
    private fun keyAgreementFor(privateKey: PrivateKey): KeyAgreement {
        return if (privateKey.format == null) {
            try {
                KeyAgreement.getInstance("ECDH", "AndroidKeyStore")
            } catch (_: NoSuchProviderException) {
                KeyAgreement.getInstance("ECDH")
            }
        } else {
            KeyAgreement.getInstance("ECDH")
        }
    }

    /**
     * HKDF-SHA256 with `ikm = point || sharedSecret`, empty salt and info —
     * Tink's ECIES-AEAD-HKDF parameters. Output is 32 bytes (AES-256).
     */
    private fun deriveAesKey(ephemeralPoint: ByteArray, sharedSecret: ByteArray): ByteArray {
        return Hkdf.computeHkdf(
            "HmacSha256",
            ephemeralPoint + sharedSecret,
            byteArrayOf(),
            byteArrayOf(),
            32
        )
    }
}
