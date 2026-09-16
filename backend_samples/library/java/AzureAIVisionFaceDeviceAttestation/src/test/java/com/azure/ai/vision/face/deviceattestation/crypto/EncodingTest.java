package com.azure.ai.vision.face.deviceattestation.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class EncodingTest {

    @Test
    void base64ToleratesUrlSafeAndMissingPadding() {
        byte[] bytes = { (byte) 0xfb, (byte) 0xff, 0x00, 0x10 };
        String std = Base64.getEncoder().withoutPadding().encodeToString(bytes);
        String urlSafe = std.replace('+', '-').replace('/', '_');
        assertArrayEquals(bytes, Base64Utils.decode(urlSafe));
    }

    @Test
    void base64DecodesStandard() {
        assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8), Base64Utils.decode("SGVsbG8="));
    }

    @Test
    void hexRoundTrip() {
        byte[] bytes = { 0x00, (byte) 0xff, 0x10, 0x2a };
        assertEquals("00ff102a", HexUtils.toHex(bytes));
        assertArrayEquals(bytes, HexUtils.fromHex("00ff102a"));
        assertArrayEquals(bytes, HexUtils.fromHex("00FF102A"));
        assertEquals("", HexUtils.toHex(new byte[0]));
        assertArrayEquals(new byte[0], HexUtils.fromHex(""));
    }

    @Test
    void hexRejectsMalformedInput() {
        for (String value : new String[] { "0", "abc", "gg", "0z", " 00", "00\n" }) {
            assertThrows(IllegalArgumentException.class, () -> HexUtils.fromHex(value), value);
        }
    }

    @Test
    void pemRoundTripsAndRejectsAmbiguousOrMalformedBlocks() {
        byte[] der = new byte[128];
        for (int index = 0; index < der.length; index++) {
            der[index] = (byte) index;
        }
        for (String label : new String[] { "CERTIFICATE", "PUBLIC KEY", "PRIVATE KEY" }) {
            String pem = PemUtils.toPem(label, der);
            assertArrayEquals(der, PemUtils.pemBodyToDer(pem));
            assertArrayEquals(der, PemUtils.pemBodyToDer(pem.replace("\n", "\r\n")));
            assertThrows(IllegalArgumentException.class, () -> PemUtils.pemBodyToDer(pem + pem));
        }
        String certificate = PemUtils.toPem("CERTIFICATE", der);
        assertArrayEquals(der, CertUtils.pemToDer(certificate));
        for (String malformed : new String[] { "AA==", "not PEM",
                "-----BEGIN CERTIFICATE-----\n!\n-----END CERTIFICATE-----",
                certificate.replace("END CERTIFICATE", "END PUBLIC KEY"),
                PemUtils.toPem("PUBLIC KEY", der) }) {
            assertThrows(IllegalArgumentException.class, () -> CertUtils.pemToDer(malformed));
        }
    }

}
