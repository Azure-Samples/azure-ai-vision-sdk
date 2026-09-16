package com.azure.ai.vision.face.deviceattestation.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.util.BigIntegers;
import org.junit.jupiter.api.Test;

import com.azure.ai.vision.face.deviceattestation.TestCrypto;
import com.google.crypto.tink.subtle.EllipticCurves;

class CryptoUtilsTest {

    @Test
    void pointEncodingMatchesJdkIncludingLeadingZeroAndHighBitCoordinates() throws Exception {
        var generator = CustomNamedCurves.getByName("secp256r1").getG();
        var keyFactory = KeyFactory.getInstance("EC");
        boolean sawLeadingZero = false;
        boolean sawHighBit = false;
        for (int scalar = 1; scalar <= 4096 && !(sawLeadingZero && sawHighBit); scalar++) {
            var point = generator.multiply(BigInteger.valueOf(scalar)).normalize();
            var key = (ECPublicKey) keyFactory.generatePublic(new ECPublicKeySpec(
                    new ECPoint(point.getAffineXCoord().toBigInteger(), point.getAffineYCoord().toBigInteger()),
                    EllipticCurves.getNistP256Params()));
            byte[] encoded = CryptoUtils.uncompressedPoint(key);
            assertEquals(65, encoded.length);
            assertEquals(4, encoded[0]);
            assertArrayEquals(SubjectPublicKeyInfo.getInstance(key.getEncoded()).getPublicKeyData().getBytes(), encoded);
            sawLeadingZero |= encoded[1] == 0 || encoded[33] == 0;
            sawHighBit |= encoded[1] < 0 || encoded[33] < 0;
        }
        assertTrue(sawLeadingZero);
        assertTrue(sawHighBit);
    }

    @Test
    void pointEncodingRejectsNonP256Keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        var key = (ECPublicKey) generator.generateKeyPair().getPublic();
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.uncompressedPoint(key));
    }

    @Test
    void generatesPemKeyPair() {
        EcKeyPair kp = CryptoUtils.generateServerKeyPairEC();
        assertNotNull(kp);
        assertTrue(kp.publicKey().contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(kp.privateKey().contains("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    void eciesRoundTrip() {
        EcKeyPair kp = CryptoUtils.generateServerKeyPairEC();
        String message = "{\"token\":\"abc\",\"n\":42}";
        String blob = CryptoUtils.encryptWithPublicKeyEC(message, kp.publicKey());
        assertNotNull(blob);
        assertEquals(message, CryptoUtils.decryptWithPrivateKeyEC(blob, kp.privateKey()));
    }

    @Test
    void decryptsIndependentlyEncryptedEciesPayload() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var recipient = generator.generateKeyPair();
        var ephemeral = generator.generateKeyPair();
        byte[] point = SubjectPublicKeyInfo.getInstance(ephemeral.getPublic().getEncoded()).getPublicKeyData().getBytes();
        var agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(recipient.getPublic(), true);
        byte[] sharedSecret = agreement.generateSecret();
        byte[] inputKeyMaterial = ByteBuffer.allocate(point.length + sharedSecret.length).put(point).put(sharedSecret).array();
        var hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(inputKeyMaterial, new byte[0], new byte[0]));
        byte[] aesKey = new byte[32];
        hkdf.generateBytes(aesKey, 0, aesKey.length);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
        String message = "Independent ECIES compatibility payload";
        byte[] ciphertextWithTag = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
        byte[] blob = ByteBuffer.allocate(point.length + iv.length + ciphertextWithTag.length)
                .put(point).put(iv).put(ciphertextWithTag).array();
        String privateKeyPem = PemUtils.toPem("PRIVATE KEY", recipient.getPrivate().getEncoded());
        assertEquals(message, CryptoUtils.decryptWithPrivateKeyEC(Base64.getEncoder().encodeToString(blob), privateKeyPem));
    }

    @Test
    void encryptionMatchesLegacyWireFormat() throws Exception {
        EcKeyPair keys = CryptoUtils.generateServerKeyPairEC();
        String message = "Legacy ECIES payload";
        byte[] blob = Base64.getDecoder().decode(CryptoUtils.encryptWithPublicKeyEC(message, keys.publicKey()));
        assertEquals(65 + 12 + message.getBytes(StandardCharsets.UTF_8).length + 16, blob.length);
        assertEquals(0x04, blob[0]);

        ECPrivateKeyParameters recipient = (ECPrivateKeyParameters) PrivateKeyFactory.createKey(PemUtils.pemBodyToDer(keys.privateKey()));
        byte[] point = Arrays.copyOfRange(blob, 0, 65);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(recipient);
        byte[] sharedSecret = BigIntegers.asUnsignedByteArray(32, agreement.calculateAgreement(new ECPublicKeyParameters(
                recipient.getParameters().getCurve().decodePoint(point), recipient.getParameters())));
        byte[] inputKeyMaterial = ByteBuffer.allocate(65 + 32).put(point).put(sharedSecret).array();
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(inputKeyMaterial, new byte[0], new byte[0]));
        byte[] aesKey = new byte[32];
        hkdf.generateBytes(aesKey, 0, aesKey.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(blob, 65, 77)));
        assertEquals(message, new String(cipher.doFinal(Arrays.copyOfRange(blob, 77, blob.length)), StandardCharsets.UTF_8));
    }

    @Test
    void eciesRejectsTamperingAndWrongKey() {
        EcKeyPair keys = CryptoUtils.generateServerKeyPairEC();
        EcKeyPair otherKeys = CryptoUtils.generateServerKeyPairEC();
        String ciphertext = CryptoUtils.encryptWithPublicKeyEC("payload", keys.publicKey());
        assertNull(CryptoUtils.decryptWithPrivateKeyEC(ciphertext, otherKeys.privateKey()));
        byte[] blob = Base64.getDecoder().decode(ciphertext);
        for (int offset : new int[] { 0, 1, 65, 77, blob.length - 1 }) {
            byte[] tampered = blob.clone();
            tampered[offset] ^= 1;
            assertNull(CryptoUtils.decryptWithPrivateKeyEC(Base64.getEncoder().encodeToString(tampered), keys.privateKey()));
        }
        assertNull(CryptoUtils.decryptWithPrivateKeyEC(Base64.getEncoder().encodeToString(Arrays.copyOf(blob, 92)), keys.privateKey()));
    }

    @Test
    void eciesSupportsEmptyPlaintext() {
        EcKeyPair keys = CryptoUtils.generateServerKeyPairEC();
        String ciphertext = CryptoUtils.encryptWithPublicKeyEC("", keys.publicKey());
        assertNotNull(ciphertext);
        assertEquals(93, Base64.getDecoder().decode(ciphertext).length);
        assertEquals("", CryptoUtils.decryptWithPrivateKeyEC(ciphertext, keys.privateKey()));
    }

    @Test
    void eciesRejectsNonP256Keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        var keys = generator.generateKeyPair();
        assertNull(CryptoUtils.encryptWithPublicKeyEC("payload", PemUtils.toPem("PUBLIC KEY", keys.getPublic().getEncoded())));
        EcKeyPair p256Keys = CryptoUtils.generateServerKeyPairEC();
        String ciphertext = CryptoUtils.encryptWithPublicKeyEC("payload", p256Keys.publicKey());
        assertNull(CryptoUtils.decryptWithPrivateKeyEC(ciphertext, PemUtils.toPem("PRIVATE KEY", keys.getPrivate().getEncoded())));
    }

    @Test
    void ecdsaVerifyAcceptsValidRejectsTampered() throws Exception {
        EcKeyPair kp = CryptoUtils.generateServerKeyPairEC();
        String data = "payload-to-sign";
        String signature = TestCrypto.signEc(data, kp.privateKey());
        assertTrue(CryptoUtils.verifySignatureEC(data, signature, kp.publicKey()));
        assertFalse(CryptoUtils.verifySignatureEC("tampered", signature, kp.publicKey()));
    }

    @Test
    void decryptRejectsGarbage() {
        EcKeyPair kp = CryptoUtils.generateServerKeyPairEC();
        assertEquals(null, CryptoUtils.decryptWithPrivateKeyEC("not-a-valid-blob", kp.privateKey()));
    }

    // Mobile SDKs often emit URL-safe, unpadded base64; the backend must accept it.
    @Test
    void ecdsaVerifyAcceptsUrlSafeUnpaddedSignature() throws Exception {
        EcKeyPair kp = CryptoUtils.generateServerKeyPairEC();
        String data = "payload-to-sign";
        byte[] rawSignature = Base64.getDecoder().decode(TestCrypto.signEc(data, kp.privateKey()));
        String urlSafeUnpadded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);
        assertTrue(CryptoUtils.verifySignatureEC(data, urlSafeUnpadded, kp.publicKey()));
    }

    @Test
    void eciesDecryptAcceptsUrlSafeUnpaddedBlob() {
        EcKeyPair kp = CryptoUtils.generateServerKeyPairEC();
        String message = "{\"ok\":true}";
        byte[] rawBlob = Base64.getDecoder().decode(CryptoUtils.encryptWithPublicKeyEC(message, kp.publicKey()));
        String urlSafeUnpadded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBlob);
        assertEquals(message, CryptoUtils.decryptWithPrivateKeyEC(urlSafeUnpadded, kp.privateKey()));
    }
}
