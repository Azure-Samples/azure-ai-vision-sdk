package com.azure.ai.vision.face.deviceattestation.ios;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import com.azure.ai.vision.face.deviceattestation.Json;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CborTest {

    @Test
    void attestedCredentialLayoutPreservesFieldsAndBoundaryErrors() throws Exception {
        var parser = AppAttestVerification.class.getDeclaredMethod("parseAttestAuthData", byte[].class);
        parser.setAccessible(true);
        for (int length : new int[] { 0, 1, 255, 256, 32768, 65535 }) {
            byte[] authData = new byte[55 + length + 1];
            Arrays.fill(authData, 0, 32, (byte) 0x42);
            Arrays.fill(authData, 32, 37, (byte) 0xff);
            Arrays.fill(authData, 37, 53, (byte) 0x61);
            authData[53] = (byte) (length >>> 8);
            authData[54] = (byte) length;
            Arrays.fill(authData, 55, 55 + length, (byte) 0xab);
            authData[55 + length] = (byte) 0xee;
            Object parsed = parser.invoke(null, (Object) authData);
            assertArrayEquals(Arrays.copyOfRange(authData, 0, 32), (byte[]) component(parsed, "rpIdHash"));
            assertEquals(255, component(parsed, "flags"));
            assertEquals(4294967295L, component(parsed, "signCount"));
            assertArrayEquals(Arrays.copyOfRange(authData, 37, 53), (byte[]) component(parsed, "aaguid"));
            assertEquals(length, component(parsed, "credIdLen"));
            assertArrayEquals(Arrays.copyOfRange(authData, 55, 55 + length), (byte[]) component(parsed, "credentialId"));
        }
        for (int length : new int[] { 0, 36, 37, 54, 55 }) {
            byte[] truncated = new byte[length];
            if (length == 55) truncated[54] = 1;
            var error = assertThrows(InvocationTargetException.class, () -> parser.invoke(null, (Object) truncated));
            String expected = length < 37 ? "authData is " + length + " bytes, < 37"
                    : length < 55 ? "authData missing attested credential data" : "authData truncated within credentialId";
            assertEquals(expected, error.getCause().getMessage());
        }
    }

    @Test
    void envelopeRecordPreservesTokenAndAssertionPairing() throws Exception {
        byte[] authData = new byte[55];
        byte[] assertionData = new byte[37];
        assertionData[36] = 1;
        byte[] signature = { 1, 2, 3 };
        var tokenOutput = new ByteArrayOutputStream();
        try (var writer = new CBORFactory().createGenerator(tokenOutput)) {
            writer.writeStartObject(null, 3);
            writer.writeStringField("fmt", "apple-appattest");
            writer.writeFieldName("authData");
            writer.writeBinary(authData);
            writer.writeFieldName("attStmt");
            writer.writeStartObject(null, 1);
            writer.writeFieldName("x5c");
            writer.writeStartArray(null, 2);
            writer.writeBinary(new byte[] { 4 });
            writer.writeBinary(new byte[] { 5 });
            writer.writeEndArray();
            writer.writeEndObject();
            writer.writeEndObject();
        }
        var assertionOutput = new ByteArrayOutputStream();
        try (var writer = new CBORFactory().createGenerator(assertionOutput)) {
            writer.writeStartObject(null, 2);
            writer.writeFieldName("signature");
            writer.writeBinary(signature);
            writer.writeFieldName("authenticatorData");
            writer.writeBinary(assertionData);
            writer.writeEndObject();
        }
        var parser = AppAttestVerification.class.getDeclaredMethod("parseEnvelope", String.class);
        parser.setAccessible(true);
        Object envelope = parser.invoke(null, Json.stringify(Map.of(
                "attestation", Base64.getEncoder().encodeToString(tokenOutput.toByteArray()),
                "assertion", Base64.getEncoder().encodeToString(assertionOutput.toByteArray()))));
        var token = assertInstanceOf(AppAttestObject.class, component(envelope, "token"));
        var assertion = assertInstanceOf(AppAttestAssertionObject.class, component(envelope, "assertion"));
        assertEquals("apple-appattest", token.fmt);
        assertArrayEquals(authData, token.authData);
        assertArrayEquals(new byte[] { 4 }, token.credCertDer);
        assertArrayEquals(new byte[] { 5 }, token.intermediateDer);
        assertArrayEquals(signature, assertion.signature);
        assertArrayEquals(assertionData, assertion.authenticatorData);
        var error = assertThrows(InvocationTargetException.class, () -> parser.invoke(null, "{}"));
        assertEquals("Invalid attestation: missing attestation field", error.getCause().getMessage());
    }

    private static Object component(Object record, String name) throws Exception {
        var accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    @Test
    void assertionCountersRemainUnsignedAndBigEndian() {
        String[] encodings = { "00000000", "01020304", "7fffffff", "80000000", "ffffffff" };
        long[] expected = { 0L, 0x01020304L, 2147483647L, 2147483648L, 4294967295L };
        for (int index = 0; index < encodings.length; index++) {
            byte[] authData = new byte[38];
            Arrays.fill(authData, 0, 32, (byte) 0x42);
            authData[32] = (byte) 0x80;
            System.arraycopy(HexFormat.of().parseHex(encodings[index]), 0, authData, 33, 4);
            authData[37] = (byte) 0xff;
            var parsed = AppAttestParsers.parseAssertionAuthData(authData);
            assertEquals(expected[index], parsed.signCount());
            assertEquals(128, parsed.flags());
            assertArrayEquals(Arrays.copyOfRange(authData, 0, 32), parsed.rpIdHash());
        }
        assertThrows(IllegalArgumentException.class,
                () -> AppAttestParsers.parseAssertionAuthData(new byte[36]));
    }

    @Test
    void libraryEncodingWorksWithAssertionParser() throws Exception {
        byte[] signature = { 1, 2, 3 };
        byte[] authData = new byte[37];
        var output = new ByteArrayOutputStream();
        try (var writer = new CBORFactory().createGenerator(output)) {
            writer.writeStartObject(null, 2);
            writer.writeFieldName("signature");
            writer.writeBinary(signature);
            writer.writeFieldName("authenticatorData");
            writer.writeBinary(authData);
            writer.writeEndObject();
        }
        var assertion = AppAttestParsers.parseAppAttestAssertion(Base64.getEncoder().encodeToString(output.toByteArray()));
        assertArrayEquals(signature, assertion.signature);
        assertArrayEquals(authData, assertion.authenticatorData);
    }

    @Test
    void rejectsMalformedOrUnsupportedValues() {
        for (String hex : List.of("", "18", "430102", "636162", "8201", "a16161", "9f01ff", "bf616101ff",
            "5f4101ff", "7f6161ff", "c001", "f5", "f6", "f93c00", "a18001", "1bffffffffffffffff",
            "a1416101", "a1c0616101", "a17f6161ff01")) {
            assertThrows(RuntimeException.class, () -> Cbor.decode(HexFormat.of().parseHex(hex)), hex);
        }
    }

    @Test
    void preservesOffsetsAndNumericKeys() {
        var decoded = Cbor.decode(HexFormat.of().parseHex("002900"), 1);
        assertEquals(-10L, decoded.value());
        assertEquals(2, decoded.nextPos());
        assertEquals(4294967296L, Cbor.decode(HexFormat.of().parseHex("1b0000000100000000")).value());
        assertEquals(Map.of(1L, 2L, "a", 3L), Cbor.decode(HexFormat.of().parseHex("a20102616103")).value());
        assertEquals(Map.of(-1L, 2L, 1L, 3L, "a", 4L),
            Cbor.decode(HexFormat.of().parseHex("a320020103616104")).value());
    }

    @Test
    void limitsNesting() {
        byte[] allowed = new byte[17];
        Arrays.fill(allowed, 0, 16, (byte) 0x81);
        Cbor.decode(allowed);
        byte[] excessive = new byte[18];
        Arrays.fill(excessive, 0, 17, (byte) 0x81);
        assertThrows(RuntimeException.class, () -> Cbor.decode(excessive));
    }

    @Test
    void decodesUnsignedInt() {
        assertEquals(10L, Cbor.decode(new byte[] { 0x0a }).value());
    }

    @Test
    void decodesTextString() {
        // 0x61 = text string length 1, 0x61 = 'a'
        assertEquals("a", Cbor.decode(new byte[] { 0x61, 0x61 }).value());
    }

    @Test
    void decodesByteString() {
        // 0x42 = byte string length 2
        assertArrayEquals(new byte[] { 0x01, 0x02 }, (byte[]) Cbor.decode(new byte[] { 0x42, 0x01, 0x02 }).value());
    }

    @Test
    void decodesArray() {
        // 0x83 = array of 3
        Object value = Cbor.decode(new byte[] { (byte) 0x83, 0x01, 0x02, 0x03 }).value();
        assertEquals(List.of(1L, 2L, 3L), value);
    }

    @Test
    @SuppressWarnings("unchecked")
    void decodesMap() {
        // 0xa1 = map of 1: "a" -> 1
        Object value = Cbor.decode(new byte[] { (byte) 0xa1, 0x61, 0x61, 0x01 }).value();
        assertInstanceOf(Map.class, value);
        Map<Object, Object> map = (Map<Object, Object>) value;
        assertEquals(1L, map.get("a"));
    }
}
