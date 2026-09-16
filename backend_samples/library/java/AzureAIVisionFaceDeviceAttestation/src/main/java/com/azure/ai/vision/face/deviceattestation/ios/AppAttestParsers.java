package com.azure.ai.vision.face.deviceattestation.ios;

import static com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants.AUTH_DATA_HEADER_BYTES;
import static com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants.FLAGS_OFFSET;
import static com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants.RP_ID_HASH_BYTES;
import static com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants.SIGN_COUNT_OFFSET;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.crypto.Base64Utils;
import com.azure.ai.vision.face.deviceattestation.crypto.CertUtils;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;

/** Decoders for the two CBOR payloads Apple's App Attest APIs return. */
public final class AppAttestParsers {

    /** Decoded first 37 bytes of authenticatorData. */
    public record AssertionAuthData(byte[] rpIdHash, int flags, long signCount) {
    }

    /** Decode the base64-CBOR attestation token (DCAppAttestService.attestKey). */
    @SuppressWarnings("unchecked")
    public static AppAttestObject parseAppAttestToken(String tokenB64) {
        byte[] tokenBuf = Base64Utils.decode(tokenB64);
        Object topRaw = Cbor.decode(tokenBuf).value();
        if (!(topRaw instanceof Map)) {
            throw new IllegalArgumentException("top-level CBOR is not a map");
        }
        Map<Object, Object> top = (Map<Object, Object>) topRaw;

        if (!(top.get("fmt") instanceof String fmt)) {
            throw new IllegalArgumentException("missing fmt");
        }
        if (!(top.get("attStmt") instanceof Map)) {
            throw new IllegalArgumentException("missing attStmt");
        }
        Map<Object, Object> attStmt = (Map<Object, Object>) top.get("attStmt");
        if (!(top.get("authData") instanceof byte[] authData)) {
            throw new IllegalArgumentException("missing authData");
        }
        if (!(attStmt.get("x5c") instanceof List<?> x5c) || x5c.size() < 2) {
            throw new IllegalArgumentException("attStmt.x5c must have >= 2 certs");
        }
        if (!(x5c.get(0) instanceof byte[] credCertDer) || !(x5c.get(1) instanceof byte[] intermediateDer)) {
            throw new IllegalArgumentException("attStmt.x5c entries must be byte strings");
        }

        byte[] receipt = attStmt.get("receipt") instanceof byte[] r ? r : null;
        return new AppAttestObject(fmt, authData, credCertDer, intermediateDer,
                receipt != null ? receipt.length : 0, receipt);
    }

    /** Decode the base64-CBOR assertion (DCAppAttestService.generateAssertion). */
    @SuppressWarnings("unchecked")
    public static AppAttestAssertionObject parseAppAttestAssertion(String assertionB64) {
        byte[] buf = Base64Utils.decode(assertionB64);
        Object topRaw = Cbor.decode(buf).value();
        if (!(topRaw instanceof Map)) {
            throw new IllegalArgumentException("assertion top-level CBOR is not a map");
        }
        Map<Object, Object> top = (Map<Object, Object>) topRaw;
        if (!(top.get("signature") instanceof byte[] signature)) {
            throw new IllegalArgumentException("assertion missing signature");
        }
        if (!(top.get("authenticatorData") instanceof byte[] authenticatorData)) {
            throw new IllegalArgumentException("assertion missing authenticatorData");
        }
        if (authenticatorData.length < AUTH_DATA_HEADER_BYTES) {
            throw new IllegalArgumentException("assertion authenticatorData is " + authenticatorData.length + " bytes, < " + AUTH_DATA_HEADER_BYTES);
        }
        return new AppAttestAssertionObject(signature, authenticatorData);
    }

    /** Decode the first 37 bytes: rpIdHash(32) || flags(1) || signCount(4). */
    public static AssertionAuthData parseAssertionAuthData(byte[] authenticatorData) {
        if (authenticatorData.length < AUTH_DATA_HEADER_BYTES) {
            throw new IllegalArgumentException("authenticatorData is " + authenticatorData.length + " bytes, < " + AUTH_DATA_HEADER_BYTES);
        }
        return new AssertionAuthData(
                Arrays.copyOfRange(authenticatorData, 0, RP_ID_HASH_BYTES),
                Byte.toUnsignedInt(authenticatorData[FLAGS_OFFSET]),
                Integer.toUnsignedLong(ByteBuffer.wrap(authenticatorData, SIGN_COUNT_OFFSET, Integer.BYTES).getInt()));
    }

    /**
     * Pull the nonce OCTET STRING out of credCert extension OID
     * 1.2.840.113635.100.8.2.
     */
    public static byte[] extractNonceFromCredCert(byte[] credCertDer) {
        byte[] container = CertUtils.getExtensionValue(credCertDer, AppAttestConstants.NONCE_OID);
        if (container == null) {
            return null;
        }
        try {
            ASN1Sequence sequence = ASN1Sequence.getInstance(container);
            if (sequence.size() != 1) return null;
            ASN1TaggedObject tagged = ASN1TaggedObject.getInstance(sequence.getObjectAt(0));
            if (!tagged.hasContextTag(1) || !tagged.isExplicit()) return null;
            return ASN1OctetString.getInstance(tagged, true).getOctets();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private AppAttestParsers() {
    }
}
