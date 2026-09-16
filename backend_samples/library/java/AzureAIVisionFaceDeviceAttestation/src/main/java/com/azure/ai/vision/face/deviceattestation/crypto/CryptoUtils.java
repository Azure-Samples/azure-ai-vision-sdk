package com.azure.ai.vision.face.deviceattestation.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.bouncycastle.crypto.ec.CustomNamedCurves;

import com.google.crypto.tink.AccessesPartialKey;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.AesGcmParameters;
import com.google.crypto.tink.hybrid.EciesParameters;
import com.google.crypto.tink.hybrid.EciesPrivateKey;
import com.google.crypto.tink.hybrid.EciesPublicKey;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.subtle.EllipticCurves;
import com.google.crypto.tink.util.SecretBigInteger;

/**
 * Elliptic-curve crypto primitives: P-256 key generation, ECDSA-SHA256
 * verification, and Tink-format ECIES (ECDH + HKDF-SHA256 + AES-256-GCM).
 * Faithfully mirrors the npm library's {@code crypto_utils.ts} so the same
 * mobile clients interoperate with either backend.
 */
public final class CryptoUtils {

    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;

    private static final EciesParameters ECIES_PARAMETERS = createEciesParameters();

    /** Generate a P-256 EC key pair, PEM-encoded (SPKI public, PKCS8 private), or null. */
    public static EcKeyPair generateServerKeyPairEC() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = kpg.generateKeyPair();
            return new EcKeyPair(
                    PemUtils.toPem("PUBLIC KEY", kp.getPublic().getEncoded()),
                    PemUtils.toPem("PRIVATE KEY", kp.getPrivate().getEncoded()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verify an ECDSA-SHA256 signature over {@code data} (UTF-8). The signature is
     * base64 and DER-encoded (Rfc3279 SEQUENCE{r,s}).
     */
    public static boolean verifySignatureEC(String data, String signatureBase64, String publicKeyPem) {
        try {
            ECPublicKey pub = parsePublicKeyPem(publicKeyPem);
            byte[] signature = Base64Utils.decode(signatureBase64);
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(pub);
            verifier.update(data.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verify an ECDSA-SHA256 signature over {@code message} trying DER
     * (Rfc3279 SEQUENCE) first, then IEEE-P1363 (raw r||s). Returns the encoding
     * that verified ("der" / "ieee-p1363"), or null if neither did.
     */
    public static String verifyEcdsaMultiFormat(ECPublicKey key, byte[] message, byte[] signature) {
        if (tryVerify("SHA256withECDSA", key, message, signature)) {
            return "der";
        }
        if (tryVerify("SHA256withECDSAinP1363Format", key, message, signature)) {
            return "ieee-p1363";
        }
        return null;
    }

    private static boolean tryVerify(String algorithm, PublicKey key, byte[] message, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Encrypt {@code data} using Tink-format ECIES for the recipient's P-256 public
     * key. Serialization: {@code ephemeralPoint(65) || iv(12) || ciphertext ||
     * authTag(16)}, base64-encoded.
     */
    public static String encryptWithPublicKeyEC(String data, String publicKeyPem) {
        try {
            ECPublicKey recipient = parsePublicKeyPem(publicKeyPem);
            requireP256(recipient.getParams());
            EciesPublicKey key = EciesPublicKey.createForNistCurve(ECIES_PARAMETERS, recipient.getW(), null);
            HybridEncrypt encryptor = KeysetHandle.newBuilder()
                .addEntry(KeysetHandle.importKey(key).withRandomId().makePrimary())
                .build().getPrimitive(RegistryConfiguration.get(), HybridEncrypt.class);
            return Base64.getEncoder().encodeToString(encryptor.encrypt(data.getBytes(StandardCharsets.UTF_8), new byte[0]));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decrypt a Tink-format ECIES blob ({@code ephemeralPoint(65) || iv(12) ||
     * ciphertext || authTag(16)}) with the recipient's P-256 private key.
     */
        @AccessesPartialKey
        public static String decryptWithPrivateKeyEC(String tinkCiphertextBase64, String privateKeyPem) {
        try {
            byte[] blob = Base64Utils.decode(tinkCiphertextBase64);
            ECPrivateKey recipient = parsePrivateKeyPem(privateKeyPem);
            requireP256(recipient.getParams());
            var publicPoint = CustomNamedCurves.getByName("secp256r1").getG().multiply(recipient.getS()).normalize();
            EciesPublicKey publicKey = EciesPublicKey.createForNistCurve(ECIES_PARAMETERS,
                new ECPoint(publicPoint.getAffineXCoord().toBigInteger(), publicPoint.getAffineYCoord().toBigInteger()), null);
            EciesPrivateKey key = EciesPrivateKey.createForNistCurve(publicKey,
                SecretBigInteger.fromBigInteger(recipient.getS(), InsecureSecretKeyAccess.get()));
            HybridDecrypt decryptor = KeysetHandle.newBuilder()
                .addEntry(KeysetHandle.importKey(key).withRandomId().makePrimary())
                .build().getPrimitive(RegistryConfiguration.get(), HybridDecrypt.class);
            byte[] plaintext = decryptor.decrypt(blob, new byte[0]);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Uncompressed P-256 point (0x04 || X(32) || Y(32)) of an EC public key. */
    public static byte[] uncompressedPoint(ECPublicKey key) {
        if (!EllipticCurves.isSameEcParameterSpec(key.getParams(), EllipticCurves.getNistP256Params())) {
            throw new IllegalArgumentException("Point encoding requires a P-256 key");
        }
        ECPoint point = key.getW();
        return CustomNamedCurves.getByName("secp256r1").getCurve()
                .validatePoint(point.getAffineX(), point.getAffineY()).getEncoded(false);
    }

    private static EciesParameters createEciesParameters() {
        try {
            HybridConfig.register();
            return EciesParameters.builder()
                    .setCurveType(EciesParameters.CurveType.NIST_P256)
                    .setHashType(EciesParameters.HashType.SHA256)
                    .setNistCurvePointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
                    .setVariant(EciesParameters.Variant.NO_PREFIX)
                    .setDemParameters(AesGcmParameters.builder()
                            .setKeySizeBytes(AES_256_KEY_BYTES)
                            .setIvSizeBytes(GCM_IV_BYTES)
                            .setTagSizeBytes(GCM_TAG_BYTES)
                            .setVariant(AesGcmParameters.Variant.NO_PREFIX).build())
                    .build();
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void requireP256(ECParameterSpec parameters) throws GeneralSecurityException {
        if (!EllipticCurves.isSameEcParameterSpec(parameters, EllipticCurves.getNistP256Params())) {
            throw new GeneralSecurityException("ECIES requires a P-256 key");
        }
    }

    private static ECPublicKey parsePublicKeyPem(String pem) throws Exception {
        byte[] der = PemUtils.pemBodyToDer(pem);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return (ECPublicKey) kf.generatePublic(new X509EncodedKeySpec(der));
    }

    private static ECPrivateKey parsePrivateKeyPem(String pem) throws Exception {
        byte[] der = PemUtils.pemBodyToDer(pem);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return (ECPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private CryptoUtils() {
    }
}
