package com.azure.ai.vision.face.deviceattestation.ios;

import static com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants.AAGUID_OFFSET;
import static com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants.AUTH_DATA_HEADER_BYTES;
import static com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants.CREDENTIAL_ID_LENGTH_OFFSET;
import static com.azure.ai.vision.face.deviceattestation.ios.AppAttestConstants.CREDENTIAL_ID_OFFSET;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.IsoTime;
import com.azure.ai.vision.face.deviceattestation.Json;
import com.azure.ai.vision.face.deviceattestation.JsonData;
import com.azure.ai.vision.face.deviceattestation.JsonParseException;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.crypto.CertUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.CryptoUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.HashUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.HexUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.PemUtils;
import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.services.AttestationMessageData;
import com.azure.ai.vision.face.deviceattestation.services.AuthVerificationResult;

/**
 * Apple App Attest verification: the registration-time attestation chain (nine
 * phases) and the per-call ongoing assertion. Faithful port of the npm
 * library's ios/ modules.
 */
public final class AppAttestVerification {

    private static final class PhaseFail extends RuntimeException {
        final String reason;

        PhaseFail(String reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    private record AssertionResult(
            byte[] assertionAuthData,
            byte[] assertionRpIdHash,
            int assertionFlags,
            long assertionSignCount,
            String signatureEncoding,
            String authCertThumbprintHex) {
    }

            private record AttestEnvelope(AppAttestObject token, AppAttestAssertionObject assertion) {
            }

    public static AuthVerificationResult verify(
            AttestationConfig config,
            AttestationLogger logger,
            AttestationMessageData messageData,
            String attestJson) {
        logger.trackEvent("IosAuth.VerifyStart",
                Maps.of("platform", "ios", "clientId", messageData.clientId, "attestJsonLength", attestJson.length()), null);

        try {
            boolean debugMode = config.debugMode();

            AttestEnvelope envelope = parseEnvelope(attestJson);
            AppAttestObject token = envelope.token();
            AppAttestAssertionObject assertion = envelope.assertion();

            X509Certificate credCert = CertUtils.loadCertificate(token.credCertDer);
            X509Certificate intCert = CertUtils.loadCertificate(token.intermediateDer);

            validateChainValidityDates(credCert, intCert);
            verifyX5cChain(token.credCertDer, token.intermediateDer);

            List<String> warnings = new ArrayList<>();

            // 4. authData layout
            AttestAuthData ad = parseAttestAuthData(token.authData);

            // 5. rpIdHash == SHA-256(IOS_APP_ID)
            AppIdCheck appIdCheck = verifyAppIdRpIdHash(ad.rpIdHash, config);

            // 6. aaguid policy (prod/dev gating)
            verifyAaguidPolicy(ad.aaguid, debugMode, warnings);

            // 7. credentialId == SHA-256(uncompressed EC point)
            if (!(credCert.getPublicKey() instanceof ECPublicKey credPubKey)) {
                throw new PhaseFail("CRED_PUBKEY_EXTRACT", "Failed to extract credCert public key");
            }
            verifyCredentialIdBindsPubKey(credPubKey, ad.credentialId);

            // 8. attestation nonce binding to the session challenge
            NonceBinding nonceBinding = verifyAttestationNonceBinding(token.credCertDer, token.authData, messageData);

            // 9. assertion proves the auth cert belongs to the attested key
            AssertionResult asr = verifyAssertionAgainstAuthCert(assertion, credPubKey, ad.rpIdHash, ad.signCount, messageData.publicCert);

            String aaguidUtf8 = trimNul(new String(ad.aaguid, StandardCharsets.US_ASCII));

            Map<String, Object> verdict = new LinkedHashMap<>();
            verdict.put("fmt", token.fmt);
            verdict.put("rpIdHash", HexUtils.toHex(ad.rpIdHash));
                verdict.put("appId", appIdCheck.expectedAppId);
            verdict.put("aaguid", aaguidUtf8);
            verdict.put("flags", ad.flags);
            verdict.put("signCount", ad.signCount);
            verdict.put("credentialId", HexUtils.toHex(ad.credentialId));
            verdict.put("credentialIdMatchesPubKey", true);
            verdict.put("credCert", certInfo(credCert, HexUtils.toHex(HashUtils.sha256(token.credCertDer))));
            verdict.put("credCertPem", PemUtils.toPem("CERTIFICATE", token.credCertDer));
            verdict.put("intermediateCert", certInfo(intCert, null));
            verdict.put("receiptLength", token.receiptLength);
            verdict.put("receipt", token.receipt != null
                    ? java.util.Base64.getEncoder().encodeToString(token.receipt) : null);
            verdict.put("authDataLength", token.authData.length);
            verdict.put("nonceExtension", HexUtils.toHex(nonceBinding.certNonce));
            verdict.put("expectedClientDataHash", HexUtils.toHex(nonceBinding.challengeBytes));
            verdict.put("authCertThumbprint", asr.authCertThumbprintHex());
            verdict.put("nonceVerified", true);

            Map<String, Object> assertionInfo = new LinkedHashMap<>();
            assertionInfo.put("authenticatorDataLength", asr.assertionAuthData().length);
            assertionInfo.put("rpIdHash", HexUtils.toHex(asr.assertionRpIdHash()));
            assertionInfo.put("flags", asr.assertionFlags());
            assertionInfo.put("signCount", asr.assertionSignCount());
            assertionInfo.put("signatureLength", assertion.signature.length);
            assertionInfo.put("signatureEncoding", asr.signatureEncoding());
            assertionInfo.put("expectedClientDataHash", asr.authCertThumbprintHex());
            assertionInfo.put("signatureVerified", true);
            verdict.put("assertion", assertionInfo);
            verdict.put("challengeBinding",
                    "attest: sha256(authData || challengeHashBytes);  assert: ECDSA-SHA256(credCertPubKey, nonce = sha256(authenticatorData || sha256(authCertDER)))");

            logger.trackEvent("IosAuth.VerifySuccess", Maps.of(
                    "clientId", messageData.clientId,
                    "aaguid", aaguidUtf8,
                    "attestSignCount", ad.signCount,
                    "assertSignCount", asr.assertionSignCount(),
                    "warningCount", warnings.size()), null);

            return AuthVerificationResult.builder()
                    .verified(true)
                    .platform("ios")
                    .message("iOS App Attest verified successfully")
                    .timestamp(IsoTime.now())
                    .chainLength(2)
                    .rootCA("Apple App Attestation Root CA")
                    .appAttestVerdict(verdict)
                    .warnings(warnings.isEmpty() ? null : warnings)
                    .build();
        } catch (PhaseFail f) {
            logger.trackEvent("IosAuth.VerifyFail",
                    Maps.of("clientId", messageData.clientId, "reason", f.reason, "message", f.getMessage()), null);
            return AuthVerificationResult.builder()
                    .verified(false).platform("ios").message(f.getMessage()).timestamp(IsoTime.now()).build();
        } catch (Exception e) {
            logger.trackException(e, Maps.of("source", "verifyiOSAuth.unexpected", "clientId", messageData.clientId));
            return AuthVerificationResult.builder()
                    .verified(false).platform("ios")
                    .message("iOS attestation verification error: " + e.getMessage())
                    .timestamp(IsoTime.now()).build();
        }
    }

    public static OngoingAssertionResult verifyOngoingAssertion(
            String credCertPem,
            byte[] blob,
            String assertionB64,
            String expectedRpIdHashHex,
            long lastSignCount) {
        ECPublicKey pub;
        try {
            X509Certificate credCert = CertUtils.loadCertificatePem(credCertPem);
            if (!(credCert.getPublicKey() instanceof ECPublicKey ec)) {
                return OngoingAssertionResult.failure("CRED_CERT_PARSE_FAIL", "Failed to parse persisted credCert PEM: not an EC key");
            }
            pub = ec;
        } catch (Exception e) {
            return OngoingAssertionResult.failure("CRED_CERT_PARSE_FAIL", "Failed to parse persisted credCert PEM: " + e.getMessage());
        }

        AppAttestAssertionObject parsed;
        try {
            parsed = AppAttestParsers.parseAppAttestAssertion(assertionB64);
        } catch (Exception e) {
            return OngoingAssertionResult.failure("ASSERTION_DECODE_ERROR", "Failed to decode App Attest assertion: " + e.getMessage());
        }

        byte[] authData = parsed.authenticatorData;
        AppAttestParsers.AssertionAuthData header = AppAttestParsers.parseAssertionAuthData(authData);
        byte[] rpIdHash = header.rpIdHash();
        long signCount = header.signCount();

        if (!HexUtils.toHex(rpIdHash).equals(expectedRpIdHashHex)) {
            return OngoingAssertionResult.failure("ASSERTION_RPID_MISMATCH", "Assertion rpIdHash does not match SHA-256(IOS_APP_ID)", signCount);
        }
        if (signCount <= lastSignCount) {
            return OngoingAssertionResult.failure("ASSERTION_SIGNCOUNT_NOT_INCREMENTED",
                    "Assertion signCount (" + signCount + ") is not greater than lastSignCount (" + lastSignCount + ")", signCount);
        }

        byte[] clientDataHash = HashUtils.sha256(blob);
        byte[] nonce = HashUtils.sha256(authData, clientDataHash);

        String encoding = CryptoUtils.verifyEcdsaMultiFormat(pub, nonce, parsed.signature);
        if (encoding == null) {
            return OngoingAssertionResult.failure("ASSERTION_SIGNATURE_INVALID",
                    "Assertion signature does not verify against credCert public key", signCount);
        }
        return OngoingAssertionResult.success(signCount);
    }

    // --- phases ---

    private static AttestEnvelope parseEnvelope(String attestJson) {
        Map<String, Object> parsed;
        try {
            parsed = Json.parseObject(attestJson);
        } catch (JsonParseException e) {
            throw new PhaseFail("ATTEST_JSON_PARSE_ERROR", "Invalid attestation: failed to parse attestJson");
        }

        String attestation = parsed == null ? null : JsonData.getString(parsed, "attestation");
        String assertion = parsed == null ? null : JsonData.getString(parsed, "assertion");
        if (attestation == null || attestation.isEmpty()) {
            throw new PhaseFail("MISSING_ATTESTATION", "Invalid attestation: missing attestation field");
        }
        if (assertion == null || assertion.isEmpty()) {
            throw new PhaseFail("MISSING_ASSERTION", "Invalid attestation: missing assertion (auth-cert binding proof)");
        }

        AppAttestObject token;
        try {
            token = AppAttestParsers.parseAppAttestToken(attestation);
        } catch (Exception e) {
            throw new PhaseFail("ATTESTATION_DECODE_ERROR", "Invalid attestation: " + e.getMessage());
        }
        if (!"apple-appattest".equals(token.fmt)) {
            throw new PhaseFail("UNEXPECTED_FMT", "Unexpected fmt: " + token.fmt);
        }

        AppAttestAssertionObject parsedAssertion;
        try {
            parsedAssertion = AppAttestParsers.parseAppAttestAssertion(assertion);
        } catch (Exception e) {
            throw new PhaseFail("ASSERTION_DECODE_ERROR", "Invalid assertion: " + e.getMessage());
        }
        return new AttestEnvelope(token, parsedAssertion);
    }

    private static void verifyX5cChain(byte[] credCertDer, byte[] intermediateDer) {
        var chain = CertUtils.matchesPinnedCA(intermediateDer, AppAttestConstants.APPLE_APP_ATTEST_ROOT_CAS)
                ? List.of(credCertDer, intermediateDer)
                : List.of(credCertDer, intermediateDer, CertUtils.pemToDer(AppAttestConstants.APPLE_APP_ATTEST_ROOT_CA_PEM));
        if (!CertUtils.validateCertificatePath(chain, AppAttestConstants.APPLE_APP_ATTEST_ROOT_CAS)) {
            throw new PhaseFail("CHAIN_PATH_INVALID", "Certificate path validation failed against the Apple App Attestation Root CA");
        }
    }

    private static void validateChainValidityDates(X509Certificate credCert, X509Certificate intCert) {
        Instant now = Instant.now();
        Map<String, X509Certificate> certs = new LinkedHashMap<>();
        certs.put("credCert", credCert);
        certs.put("intermediate", intCert);
        for (Map.Entry<String, X509Certificate> e : certs.entrySet()) {
            Instant vf = e.getValue().getNotBefore().toInstant();
            Instant vt = e.getValue().getNotAfter().toInstant();
            if (now.isBefore(vf) || now.isAfter(vt)) {
                throw new PhaseFail("CHAIN_CERT_NOT_VALID",
                        e.getKey() + " is not valid (valid from " + IsoTime.from(vf) + " to " + IsoTime.from(vt) + ")");
            }
        }
    }

    private record AttestAuthData(byte[] rpIdHash, int flags, long signCount, byte[] aaguid, int credIdLen, byte[] credentialId) {
    }

    private static AttestAuthData parseAttestAuthData(byte[] authData) {
        if (authData.length < AUTH_DATA_HEADER_BYTES) {
            throw new PhaseFail("AUTHDATA_TOO_SHORT", "authData is " + authData.length + " bytes, < " + AUTH_DATA_HEADER_BYTES);
        }
        AppAttestParsers.AssertionAuthData header = AppAttestParsers.parseAssertionAuthData(authData);
        if (authData.length < CREDENTIAL_ID_OFFSET) {
            throw new PhaseFail("AUTHDATA_MISSING_ATTESTED", "authData missing attested credential data");
        }
        byte[] aaguid = Arrays.copyOfRange(authData, AAGUID_OFFSET, CREDENTIAL_ID_LENGTH_OFFSET);
        int credIdLen = Short.toUnsignedInt(ByteBuffer.wrap(authData, CREDENTIAL_ID_LENGTH_OFFSET, Short.BYTES).getShort());
        if (authData.length < CREDENTIAL_ID_OFFSET + credIdLen) {
            throw new PhaseFail("AUTHDATA_TRUNCATED", "authData truncated within credentialId");
        }
        byte[] credentialId = Arrays.copyOfRange(authData, CREDENTIAL_ID_OFFSET, CREDENTIAL_ID_OFFSET + credIdLen);
        return new AttestAuthData(header.rpIdHash(), header.flags(), header.signCount(), aaguid, credIdLen, credentialId);
    }

    private record AppIdCheck(String expectedAppId) {
    }

    private static AppIdCheck verifyAppIdRpIdHash(byte[] rpIdHash, AttestationConfig config) {
        String expectedAppId = config.iosAppId();
        if (expectedAppId == null || expectedAppId.isEmpty()) {
            throw new PhaseFail("MISSING_APP_ID_ENV", "IOS_APP_ID environment variable not set (expected \"<TeamID>.<BundleID>\")");
        }
        byte[] expectedRpIdHash = HashUtils.sha256(expectedAppId.getBytes(StandardCharsets.UTF_8));
        if (!Arrays.equals(expectedRpIdHash, rpIdHash)) {
            throw new PhaseFail("APP_ID_MISMATCH", "authData.rpIdHash does not match SHA-256(IOS_APP_ID)");
        }
        return new AppIdCheck(expectedAppId);
    }

    private static void verifyAaguidPolicy(byte[] aaguid, boolean debugMode, List<String> warnings) {
        boolean isProd = Arrays.equals(aaguid, AppAttestConstants.AAGUID_PROD);
        boolean isDev = Arrays.equals(aaguid, AppAttestConstants.AAGUID_DEV);
        if (!isProd && !isDev) {
            throw new PhaseFail("UNKNOWN_AAGUID", "Unknown aaguid in authData: " + HexUtils.toHex(aaguid));
        }
        if (isDev && !debugMode) {
            throw new PhaseFail("DEV_AAGUID_NOT_ALLOWED", "authData.aaguid is \"appattestdevelop\" but DEBUG_MODE is not enabled");
        }
        if (isDev) {
            warnings.add("aaguid is \"appattestdevelop\" (debug mode)");
        }
    }

    private static void verifyCredentialIdBindsPubKey(ECPublicKey credPubKey, byte[] credentialId) {
        byte[] point = CryptoUtils.uncompressedPoint(credPubKey);
        if (point.length != 65 || (point[0] & 0xff) != 0x04) {
            throw new PhaseFail("CRED_PUBKEY_FORMAT", "credCert public key is not uncompressed P-256");
        }
        byte[] expected = HashUtils.sha256(point);
        if (!Arrays.equals(expected, credentialId)) {
            throw new PhaseFail("CREDENTIAL_ID_MISMATCH", "credentialId in authData does not match SHA-256 of credCert public key");
        }
    }

    private record NonceBinding(byte[] certNonce, byte[] challengeBytes) {
    }

    private static NonceBinding verifyAttestationNonceBinding(byte[] credCertDer, byte[] authData, AttestationMessageData messageData) {
        if (messageData.publicCert == null || messageData.publicCert.isEmpty()) {
            throw new PhaseFail("MISSING_PUBLIC_CERT", "Missing publicCert in message data");
        }
        if (messageData.challengeHash == null || messageData.challengeHash.isEmpty()) {
            throw new PhaseFail("MISSING_CHALLENGE_HASH", "Missing challengeHash in message data");
        }
        if (!isHex64(messageData.challengeHash)) {
            throw new PhaseFail("CHALLENGE_HASH_FORMAT", "challengeHash must be a 64-char hex string");
        }
        byte[] challengeBytes = HexUtils.fromHex(messageData.challengeHash);
        byte[] expectedNonce = HashUtils.sha256(authData, challengeBytes);

        byte[] certNonce = AppAttestParsers.extractNonceFromCredCert(credCertDer);
        if (certNonce == null) {
            throw new PhaseFail("CRED_NONCE_EXT_MISSING", "credCert is missing the App Attest nonce extension (OID 1.2.840.113635.100.8.2)");
        }
        if (!Arrays.equals(expectedNonce, certNonce)) {
            throw new PhaseFail("CHALLENGE_NONCE_MISMATCH", "App Attest challenge binding failed: cert nonce does not match SHA-256(authData || challengeHash bytes)");
        }
        return new NonceBinding(certNonce, challengeBytes);
    }

    private static AssertionResult verifyAssertionAgainstAuthCert(
            AppAttestAssertionObject assertion,
            ECPublicKey credPubKey,
            byte[] attestRpIdHash,
            long attestSignCount,
            String authCertPem) {
        byte[] authCertDer = CertUtils.pemToDer(authCertPem);
        byte[] authCertThumbprint = HashUtils.sha256(authCertDer);
        String authCertThumbprintHex = HexUtils.toHex(authCertThumbprint);

        byte[] assertionAuthData = assertion.authenticatorData;
        AppAttestParsers.AssertionAuthData parsed = AppAttestParsers.parseAssertionAuthData(assertionAuthData);

        if (!Arrays.equals(parsed.rpIdHash(), attestRpIdHash)) {
            throw new PhaseFail("ASSERTION_RPID_MISMATCH", "Assertion rpIdHash does not match attestation rpIdHash");
        }
        if (parsed.signCount() <= attestSignCount) {
            throw new PhaseFail("ASSERTION_SIGNCOUNT_NOT_INCREMENTED",
                    "Assertion signCount (" + parsed.signCount() + ") is not greater than attestation signCount (" + attestSignCount + ")");
        }

        byte[] assertionNonce = HashUtils.sha256(assertionAuthData, authCertThumbprint);
        String encoding = CryptoUtils.verifyEcdsaMultiFormat(credPubKey, assertionNonce, assertion.signature);
        if (encoding == null) {
            throw new PhaseFail("ASSERTION_SIGNATURE_INVALID",
                    "Assertion signature does not verify against credCert public key for nonce = SHA-256(authenticatorData || sha256(authCertDER))");
        }

        return new AssertionResult(assertionAuthData, parsed.rpIdHash(), parsed.flags(), parsed.signCount(), encoding, authCertThumbprintHex);
    }

    // --- helpers ---

    private static boolean isHex64(String s) {
        if (s.length() != 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String trimNul(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\0') {
            end--;
        }
        return s.substring(0, end);
    }

    private static Map<String, Object> certInfo(X509Certificate c, String thumbprint) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("subject", c.getSubjectX500Principal().getName());
        info.put("issuer", c.getIssuerX500Principal().getName());
        info.put("serialNumber", c.getSerialNumber().toString(16).toUpperCase());
        info.put("validFrom", IsoTime.from(c.getNotBefore().toInstant()));
        info.put("validTo", IsoTime.from(c.getNotAfter().toInstant()));
        info.put("thumbprint", thumbprint);
        return info;
    }

    private AppAttestVerification() {
    }
}
