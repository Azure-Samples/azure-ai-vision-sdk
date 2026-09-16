package com.azure.ai.vision.face.deviceattestation.crypto;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPathValidator;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bouncycastle.asn1.ASN1OctetString;

/** Certificate parsing, thumbprinting, validation, and chain-link verification. */
public final class CertUtils {

    public static byte[] getExtensionValue(byte[] certDer, String oid) {
        try {
            byte[] wrapped = parseDer(certDer).getExtensionValue(oid);
            return wrapped == null ? null : ASN1OctetString.getInstance(wrapped).getOctets();
        } catch (CertificateException | RuntimeException exception) {
            return null;
        }
    }

    public static boolean validateCertificatePath(List<byte[]> chainDer, Iterable<String> pinnedCAs) {
        try {
            if (chainDer.size() < 2 || !matchesPinnedCA(chainDer.get(chainDer.size() - 1), pinnedCAs)) {
                return false;
            }
            List<X509Certificate> certificates = new ArrayList<>();
            Date now = new Date();
            for (byte[] der : chainDer) {
                X509Certificate certificate = parseDer(der);
                certificate.checkValidity(now);
                if (!Arrays.equals(der, certificate.getEncoded()) || certificates.contains(certificate)) return false;
                certificates.add(certificate);
            }
            X509Certificate root = certificates.get(certificates.size() - 1);
            PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
            parameters.setRevocationEnabled(false);
            parameters.setDate(now);
            CertPathValidator.getInstance("PKIX").validate(
                    CertificateFactory.getInstance("X.509").generateCertPath(certificates), parameters);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static final String PEM_HEADER = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_FOOTER = "-----END CERTIFICATE-----";

    /** Order (n) of the prime256v1 (secp256r1 / P-256) named curve. */
    private static final BigInteger P256_ORDER =
            new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);

    /**
     * Clock-skew tolerance applied to certificate validity checks. Kept small
     * (5 minutes): the issuer backdates notBefore for real-world latency, so the
     * server-side window only needs to absorb its own NTP drift.
     */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    /** Convert a PEM certificate to DER bytes. */
    public static byte[] pemToDer(String pemCert) {
        return PemUtils.pemBodyToDer(pemCert, "CERTIFICATE");
    }

    /** Compute the SHA-256 thumbprint (lowercase hex) of a PEM certificate, or null. */
    public static String computeCertThumbprint(String pemCert) {
        try {
            byte[] der = pemToDer(pemCert);
            return HexUtils.toHex(MessageDigest.getInstance("SHA-256").digest(der));
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse a PEM certificate and check it is currently within its validity window. */
    public static CertificateValidationResult validateCertificate(String pemCert) {
        try {
            X509Certificate cert = parsePem(pemCert);
            Instant now = Instant.now();
            Instant validFrom = cert.getNotBefore().toInstant();
            Instant validTo = cert.getNotAfter().toInstant();
            if (now.isBefore(validFrom) || now.isAfter(validTo)) {
                return new CertificateValidationResult(false, null, null, null, null);
            }
            return new CertificateValidationResult(
                    true,
                    cert.getSubjectX500Principal().getName(),
                    cert.getIssuerX500Principal().getName(),
                    validFrom,
                    validTo);
        } catch (Exception e) {
            return null;
        }
    }

    /** Check certificate expiration with a symmetric 5-minute clock-skew tolerance. */
    public static CertificateExpirationInfo validateCertificateExpiration(String pemCert) {
        try {
            X509Certificate cert = parsePem(pemCert);
            Instant notBefore = cert.getNotBefore().toInstant();
            Instant notAfter = cert.getNotAfter().toInstant();
            Instant now = Instant.now();
            boolean isExpired = now.minus(CLOCK_SKEW).isAfter(notAfter);
            boolean isNotYetValid = now.plus(CLOCK_SKEW).isBefore(notBefore);
            return new CertificateExpirationInfo(!isExpired && !isNotYetValid, notBefore, notAfter, isExpired, isNotYetValid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract the EC public key from a certificate as SPKI PEM, validating that
     * the curve is P-256. Returns null on error or non-P-256 keys.
     */
    public static String extractPublicKeyFromCert(String pemCert) {
        try {
            X509Certificate cert = parsePem(pemCert);
            PublicKey pk = cert.getPublicKey();
            if (!(pk instanceof ECPublicKey ec) || !isP256(ec.getParams())) {
                return null;
            }
            return PemUtils.toPem("PUBLIC KEY", pk.getEncoded());
        } catch (Exception e) {
            return null;
        }
    }

    /** Load an X.509 certificate from DER bytes. */
    public static X509Certificate loadCertificate(byte[] der) throws CertificateException {
        return parseDer(der);
    }

    /** Load an X.509 certificate from a PEM string. */
    public static X509Certificate loadCertificatePem(String pem) throws CertificateException {
        return parsePem(pem);
    }

    /** Return true if {@code certDer} byte-matches any pinned CA (PEM). */
    public static boolean matchesPinnedCA(byte[] certDer, Iterable<String> pinnedCAs) {
        try {
            for (String pinnedCA : pinnedCAs) {
                if (Arrays.equals(certDer, PemUtils.pemBodyToDer(pinnedCA))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isP256(ECParameterSpec spec) {
        return spec != null
                && spec.getCurve().getField().getFieldSize() == 256
                && P256_ORDER.equals(spec.getOrder());
    }

    private static X509Certificate parsePem(String pem) throws CertificateException {
        return parseDer(PemUtils.pemBodyToDer(pem));
    }

    private static X509Certificate parseDer(byte[] der) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
    }

    private CertUtils() {
    }
}
