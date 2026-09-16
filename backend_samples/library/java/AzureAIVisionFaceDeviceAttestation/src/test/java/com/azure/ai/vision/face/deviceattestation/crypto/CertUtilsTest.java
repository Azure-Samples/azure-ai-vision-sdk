package com.azure.ai.vision.face.deviceattestation.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.math.BigInteger;
import java.util.function.Function;
import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;

import com.azure.ai.vision.face.deviceattestation.android.KeymasterExt;
import com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants;
import com.azure.ai.vision.face.deviceattestation.ios.AppAttestParsers;

class CertUtilsTest {
    @Test
    void validatesPinnedOrderedCertificatePaths() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    var rootKeys = generator.generateKeyPair();
    var issuerKeys = generator.generateKeyPair();
    var leafKeys = generator.generateKeyPair();
    long now = System.currentTimeMillis();
    Date before = new Date(now - 86400000L);
    Date after = new Date(now + 86400000L);
    for (String scenario : List.of("valid", "untrusted", "non-ca", "key-usage", "path-length", "expired",
        "future", "expired-root", "critical-extension", "signature", "missing", "reordered", "extra")) {
        byte[] root = pathCertificate("CN=Path Root", "CN=Path Root", rootKeys, rootKeys, before,
            scenario.equals("expired-root") ? new Date(now - 3600000L) : after,
            new Extension(Extension.basicConstraints, true, new BasicConstraints(scenario.equals("path-length") ? 0 : 1).getEncoded()),
            new Extension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign).getEncoded()));
        byte[] issuer = pathCertificate("CN=Path Issuer", "CN=Path Root", issuerKeys, rootKeys, before, after,
            new Extension(Extension.basicConstraints, true, new BasicConstraints(!scenario.equals("non-ca")).getEncoded()),
            new Extension(Extension.keyUsage, true, new KeyUsage(scenario.equals("key-usage") ? KeyUsage.digitalSignature : KeyUsage.keyCertSign).getEncoded()));
        Extension[] leafExtensions = scenario.equals("critical-extension")
            ? new Extension[] { new Extension(new ASN1ObjectIdentifier("1.2.3.4"), true, new byte[] { 5, 0 }) }
            : new Extension[0];
        byte[] leaf = pathCertificate("CN=Path Leaf", "CN=Path Issuer", leafKeys, issuerKeys,
            scenario.equals("future") ? new Date(now + 3600000L) : before,
            scenario.equals("expired") ? new Date(now - 3600000L) : after, leafExtensions);
        List<byte[]> chain = new ArrayList<>(List.of(leaf, issuer, root));
        if (scenario.equals("signature")) leaf[leaf.length - 1] ^= 1;
        if (scenario.equals("missing")) chain.remove(1);
        if (scenario.equals("reordered")) { chain.set(0, issuer); chain.set(1, leaf); }
        if (scenario.equals("extra")) chain.add(2, root);
        List<String> roots = scenario.equals("untrusted") ? List.of() : List.of(PemUtils.toPem("CERTIFICATE", root));
        assertEquals(scenario.equals("valid"), CertUtils.validateCertificatePath(chain, roots), scenario);
        assertFalse(CertUtils.validateCertificatePath(List.of(root), roots));
        assertFalse(CertUtils.validateCertificatePath(List.of(new byte[] { 0x30 }, root), roots));
            assertFalse(CertUtils.validateCertificatePath(List.of(Arrays.copyOf(leaf, leaf.length + 1), issuer, root), roots));
    }
    }

    private static byte[] pathCertificate(String subject, String issuer, KeyPair subjectKeys, KeyPair issuerKeys,
        Date before, Date after, Extension... extensions) throws Exception {
    var algorithm = new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.2.840.10045.4.3.2"));
    var builder = new V3TBSCertificateGenerator();
    builder.setSerialNumber(new ASN1Integer(BigInteger.valueOf(Integer.toUnsignedLong(subject.hashCode()))));
    builder.setSignature(algorithm);
    builder.setIssuer(new X500Name(issuer));
    builder.setSubject(new X500Name(subject));
    builder.setStartDate(new Time(before));
    builder.setEndDate(new Time(after));
    builder.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(subjectKeys.getPublic().getEncoded()));
    if (extensions.length > 0) builder.setExtensions(new Extensions(extensions));
    var tbs = builder.generateTBSCertificate();
    var signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(issuerKeys.getPrivate());
    signer.update(tbs.getEncoded("DER"));
    return new DERSequence(new ASN1Encodable[] { tbs, algorithm, new DERBitString(signer.sign()) }).getEncoded("DER");
    }

    @Test
    void appleExtensionLookupIgnoresEmbeddedOid() throws Exception {
        byte[] nonce = new byte[32];
        Arrays.fill(nonce, (byte) 0xab);
        byte[] payload = tlv(0x30, tlv(0xa1, tlv(0x04, nonce)));
        checkLookup(AppAttestConstants.NONCE_OID,
                payload, nonce, AppAttestParsers::extractNonceFromCredCert);
    }

    @Test
    void androidExtensionLookupIgnoresEmbeddedOid() throws Exception {
        byte[] challenge = new byte[32];
        Arrays.fill(challenge, (byte) 0xcd);
        byte[] payload = tlv(0x30, concat(HexUtils.fromHex("0201010a01010201020a0101"),
                tlv(0x04, challenge), tlv(0x04, new byte[0]), tlv(0x30, new byte[0]), tlv(0x30, new byte[0])));
        byte[] oidDer = new ASN1ObjectIdentifier(KeymasterExt.KEYMASTER_EXT_OID).getEncoded("DER");
        var description = KeymasterExt.parseKeyDescription(certificate(extension(oidDer, payload, false)));
        assertEquals(1, description.attestationVersion());
        assertEquals(1, description.attestationSecurityLevel());
        assertEquals(2, description.keyMintVersion());
        assertEquals(1, description.keyMintSecurityLevel());
        checkLookup(KeymasterExt.KEYMASTER_EXT_OID,
                payload, challenge, KeymasterExt::extractAttestationChallengeFromCert);
    }

    @Test
    void hardwareAttestationSecurityLevelAcceptsOnlyTeeOrStrongBox() {
        assertFalse(KeymasterExt.isHardwareAttestationSecurityLevel(null));
        assertFalse(KeymasterExt.isHardwareAttestationSecurityLevel(keyDescription(0)));
        assertTrue(KeymasterExt.isHardwareAttestationSecurityLevel(keyDescription(1)));
        assertTrue(KeymasterExt.isHardwareAttestationSecurityLevel(keyDescription(2)));
        assertFalse(KeymasterExt.isHardwareAttestationSecurityLevel(keyDescription(3)));
    }

    private static KeymasterExt.KeyDescription keyDescription(int attestationSecurityLevel) {
        return new KeymasterExt.KeyDescription(1, attestationSecurityLevel, 2, 1, new byte[32]);
    }

    private static void checkLookup(String oid, byte[] payload, byte[] expected,
            Function<byte[], byte[]> extract) throws Exception {
        byte[] oidDer = new ASN1ObjectIdentifier(oid).getEncoded("DER");
        byte[] wrongTag = payload.clone();
        wrongTag[2] = 0x05;
        byte[] nestedOrNegative = payload.clone();
        if (oid.equals(AppAttestConstants.NONCE_OID)) {
            nestedOrNegative = tlv(0x30, tlv(0xa1, concat(tlv(0x04, expected), HexUtils.fromHex("0500"))));
        } else {
            nestedOrNegative[4] = (byte) 0xff;
        }
        for (byte[] malformed : new byte[][] {
                new byte[0], new byte[] { 0x30 }, Arrays.copyOf(payload, payload.length - 1),
                wrongTag, concat(payload, HexUtils.fromHex("0500")), nestedOrNegative }) {
            assertNull(extract.apply(certificate(extension(oidDer, malformed, false))));
        }
        byte[] decoy = extension(HexUtils.fromHex("06032a0304"), concat(oidDer, tlv(0x04, payload)), false);
        byte[] missing = certificate(decoy);
        assertNull(CertUtils.getExtensionValue(missing, oid));
        assertNull(extract.apply(missing));
        assertNull(extract.apply(new byte[] { 0x30, 0x00 }));
        for (boolean critical : new boolean[] { false, true }) {
            byte[] real = extension(oidDer, payload, critical);
            byte[] cert = certificate(decoy, real);
            assertArrayEquals(payload, CertUtils.getExtensionValue(cert, oid));
            assertArrayEquals(expected, extract.apply(cert));
            assertNull(extract.apply(certificate(real, real)));
        }
        byte[] longPayload = new byte[300];
        assertArrayEquals(longPayload,
                CertUtils.getExtensionValue(certificate(extension(oidDer, longPayload, false)), oid));
    }

    private static byte[] certificate(byte[]... extensions) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var keys = generator.generateKeyPair();
        byte[] algorithm = HexUtils.fromHex("300a06082a8648ce3d040302");
        byte[] name = new X500Principal("CN=extension-test").getEncoded();
        byte[] validity = tlv(0x30, concat(
                tlv(0x17, "250101000000Z".getBytes(StandardCharsets.US_ASCII)),
                tlv(0x17, "350101000000Z".getBytes(StandardCharsets.US_ASCII))));
        byte[] tbs = tlv(0x30, concat(HexUtils.fromHex("a003020102020101"), algorithm, name, validity,
                name, keys.getPublic().getEncoded(), tlv(0xa3, tlv(0x30, concat(extensions)))));
        var signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keys.getPrivate());
        signer.update(tbs);
        return tlv(0x30, concat(tbs, algorithm, tlv(0x03, concat(new byte[] { 0 }, signer.sign()))));
    }

    private static byte[] extension(byte[] oid, byte[] payload, boolean critical) {
        return tlv(0x30, concat(oid, critical ? HexUtils.fromHex("0101ff") : new byte[0], tlv(0x04, payload)));
    }

    private static byte[] tlv(int tag, byte[] value) {
        var output = new ByteArrayOutputStream();
        output.write(tag);
        if (value.length < 128) {
            output.write(value.length);
        } else if (value.length < 256) {
            output.write(0x81);
            output.write(value.length);
        } else {
            output.write(0x82);
            output.write(value.length >> 8);
            output.write(value.length & 0xff);
        }
        output.writeBytes(value);
        return output.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        var output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.writeBytes(part);
        }
        return output.toByteArray();
    }
}