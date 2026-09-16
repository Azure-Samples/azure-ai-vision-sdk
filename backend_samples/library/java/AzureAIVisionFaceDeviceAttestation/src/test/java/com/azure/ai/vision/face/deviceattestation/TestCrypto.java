package com.azure.ai.vision.face.deviceattestation;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import com.azure.ai.vision.face.deviceattestation.crypto.PemUtils;

/** Test-side signing helper — mirrors what the mobile client does. */
public final class TestCrypto {

    /** ECDSA-SHA256 sign {@code data} (UTF-8) with a PKCS8 PEM private key; base64 (DER). */
    public static String signEc(String data, String privateKeyPem) throws Exception {
        byte[] der = PemUtils.pemBodyToDer(privateKeyPem);
        PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key);
        signer.update(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private TestCrypto() {
    }
}
