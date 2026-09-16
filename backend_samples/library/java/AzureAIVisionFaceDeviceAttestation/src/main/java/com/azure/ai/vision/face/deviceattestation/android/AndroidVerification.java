package com.azure.ai.vision.face.deviceattestation.android;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.azure.ai.vision.face.deviceattestation.IsoTime;
import com.azure.ai.vision.face.deviceattestation.Json;
import com.azure.ai.vision.face.deviceattestation.JsonParseException;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.crypto.Base64Utils;
import com.azure.ai.vision.face.deviceattestation.crypto.CertUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.HexUtils;
import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.services.AttestationMessageData;
import com.azure.ai.vision.face.deviceattestation.services.AuthVerificationResult;

/**
 * Android Key Attestation chain verification + Play Integrity verdict checks.
 * Faithful port of the npm library's android/ modules.
 */
public final class AndroidVerification {

    private static final class PhaseFail extends RuntimeException {
        final String reason;

        PhaseFail(String reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    private record AndroidAttestationJson(String token, List<String> certificateChain) {
    }

    public static AuthVerificationResult verify(
            AttestationConfig config,
            AttestationLogger logger,
            AttestationMessageData messageData,
            String attestJson) {
        logger.trackEvent("AndroidAuth.VerifyStart",
                Maps.of("platform", "android", "clientId", messageData.clientId, "attestJsonLength", attestJson.length()), null);

        try {
            boolean debugMode = config.debugMode();

            // 1. parse + structural validation
            AndroidAttestationJson attestData = parseAttestJson(attestJson);

            // 2. build DER buffers from base64 chain + PEM leaf
            List<byte[]> chain = buildChainDerBuffers(attestData, messageData.publicCert);
            byte[] leaf = CertUtils.pemToDer(messageData.publicCert);

            // 3. leaf <-> chain[0] binding
            verifyLeafBoundToChain0(leaf, chain);

            // 4. validity dates (leaf warning, chain hard-fail)
            String leafCertValidityWarning = validateChainValidityDates(leaf, chain, logger);

            // 5. chain signatures + pinned root CA
            String rootCASubject = verifyChainSignaturesAndRoot(chain);

            // 6. attestation must be issued by TEE or StrongBox
            verifyHardwareAttestationSecurityLevel(chain.get(0));

            // 7. revocation list lookup (fails closed)
            verifyChainRevocations(leaf, chain, logger);

            // 8. keymaster extension binds the session challenge
            String chainChallengeHex = verifyChainBindsSessionChallenge(chain.get(0), messageData.challengeHash);

            // 9. Play Integrity verdict. Required, unless the API was unavailable /
            //    quota-exceeded and the operator opted into
            //    allowAndroidAttestationWhenGoogleUnavailable — in which case the
            //    hardware Key Attestation above (incl. the session-bound keymaster
            //    challenge in step 7) stands on its own and the verdict is skipped.
            if (attestData.token() == null || attestData.token().isEmpty()) {
                throw new PhaseFail("MISSING_INTEGRITY_TOKEN", "Play Integrity token is required but not provided");
            }
            PlayIntegrityApi.IntegrityVerdictResult integrityResult =
                    PlayIntegrityApi.decryptAndVerifyIntegrityVerdict(config, logger, attestData.token());

            List<String> warnings = new ArrayList<>();
            PlayIntegrityVerdict integrityVerdict = null;

            if (integrityResult.ok) {
                integrityVerdict = integrityResult.verdict;
            } else if (integrityResult.tolerable && config.allowAndroidAttestationWhenGoogleUnavailable()) {
                String warning = "Play Integrity verdict unavailable (" + integrityResult.reason
                        + "); accepted on Key Attestation alone via allowAndroidAttestationWhenGoogleUnavailable";
                warnings.add(warning);
                logger.trackEvent("AndroidAuth.IntegrityUnavailableAccepted",
                        Maps.of("clientId", messageData.clientId, "reason", integrityResult.reason), null);
            } else {
                throw new PhaseFail("INTEGRITY_DECRYPT_FAIL", "Play Integrity verification failed: could not decrypt or verify integrity token");
            }

            if (integrityVerdict != null) {
                integrityVerdict.attestationChallenge = chainChallengeHex;

                // 10. requestHash binds the integrity token to the auth key
                throwIfFailed(IntegrityChecks.verifyRequestHash(integrityVerdict, leaf));

                // 11. timestamp freshness
                throwIfFailed(IntegrityChecks.verifyTimestamp(integrityVerdict));

                // 12. appIntegrity + deviceIntegrity + environment warnings
                throwIfFailed(IntegrityChecks.evaluate(integrityVerdict, config, debugMode, warnings));
            }

            logger.trackEvent("AndroidAuth.VerifySuccess", Maps.of(
                    "clientId", messageData.clientId,
                    "chainLength", chain.size() + 1,
                    "rootCA", rootCASubject,
                    "integritySkipped", integrityVerdict == null,
                    "warningCount", warnings.size()), null);

            return AuthVerificationResult.builder()
                    .verified(true)
                    .platform("android")
                    .message(integrityVerdict != null
                            ? "Android Key Attestation and Play Integrity verified successfully"
                            : "Android Key Attestation verified successfully (Play Integrity unavailable, accepted by policy)")
                    .timestamp(IsoTime.now())
                    .chainLength(chain.size() + 1)
                    .rootCA(rootCASubject)
                    .integrityVerdict(integrityVerdict)
                    .leafCertValidityWarning(leafCertValidityWarning)
                    .warnings(warnings.isEmpty() ? null : warnings)
                    .build();
        } catch (PhaseFail f) {
            logger.trackEvent("AndroidAuth.VerifyFail",
                    Maps.of("clientId", messageData.clientId, "reason", f.reason, "message", f.getMessage()), null);
            return AuthVerificationResult.builder()
                    .verified(false).platform("android").message(f.getMessage()).timestamp(IsoTime.now()).build();
        } catch (Exception e) {
            logger.trackException(e, Maps.of("source", "verifyAndroidAuth.unexpected", "clientId", messageData.clientId));
            return AuthVerificationResult.builder()
                    .verified(false).platform("android")
                    .message("Android attestation verification error: " + e.getMessage())
                    .timestamp(IsoTime.now()).build();
        }
    }

    // --- chain phases ---

    @SuppressWarnings("unchecked")
    private static AndroidAttestationJson parseAttestJson(String attestJson) {
        java.util.Map<String, Object> parsed;
        try {
            parsed = Json.parseObject(attestJson);
        } catch (JsonParseException e) {
            throw new PhaseFail("ATTEST_JSON_PARSE_ERROR", "Invalid attestation: failed to parse attestJson");
        }
        Object chainObj = parsed == null ? null : parsed.get("certificateChain");
        if (!(chainObj instanceof List)) {
            throw new PhaseFail("INVALID_CHAIN_FORMAT", "Invalid attestation: missing or invalid certificateChain");
        }
        List<Object> rawChain = (List<Object>) chainObj;
        if (rawChain.isEmpty()) {
            throw new PhaseFail("EMPTY_CHAIN", "Invalid attestation: empty certificateChain");
        }
        List<String> chain = new ArrayList<>(rawChain.size());
        for (Object o : rawChain) {
            chain.add((String) o);
        }
        String token = parsed.get("token") instanceof String s ? s : null;
        return new AndroidAttestationJson(token, chain);
    }

    private static List<byte[]> buildChainDerBuffers(AndroidAttestationJson attestData, String publicCertPem) {
        if (publicCertPem == null || publicCertPem.isEmpty()) {
            throw new PhaseFail("MISSING_PUBLIC_CERT", "Missing publicCert in message data");
        }
        List<byte[]> chain = new ArrayList<>(attestData.certificateChain().size());
        for (String b64 : attestData.certificateChain()) {
            chain.add(Base64Utils.decode(b64));
        }
        return chain;
    }

    private static void verifyLeafBoundToChain0(byte[] leaf, List<byte[]> chain) {
        boolean leafMatchesChain0 = Arrays.equals(leaf, chain.get(0));
        if (leafMatchesChain0) {
            return;
        }
        throw new PhaseFail("LEAF_CHAIN_MISMATCH",
                "Certificate chain verification failed: publicCert must match certificateChain[0]");
    }

    private static String validateChainValidityDates(byte[] leaf, List<byte[]> chain, AttestationLogger logger) {
        Instant now = Instant.now();
        String leafCertValidityWarning = null;

        try {
            X509Certificate leafCert = CertUtils.loadCertificate(leaf);
            Instant vf = leafCert.getNotBefore().toInstant();
            Instant vt = leafCert.getNotAfter().toInstant();
            if (now.isBefore(vf) || now.isAfter(vt)) {
                leafCertValidityWarning = "Leaf certificate validity warning: valid from " + IsoTime.from(vf) + " to " + IsoTime.from(vt);
                logger.trackEvent("AndroidAuth.LeafCertValidityWarning",
                        Maps.of("validFrom", IsoTime.from(vf), "validTo", IsoTime.from(vt)), null);
            }
        } catch (Exception e) {
            // A malformed leaf is caught by later phases; no warning here.
        }

        for (int i = 0; i < chain.size(); i++) {
            try {
                X509Certificate cert = CertUtils.loadCertificate(chain.get(i));
                Instant vf = cert.getNotBefore().toInstant();
                Instant vt = cert.getNotAfter().toInstant();
                if (now.isBefore(vf) || now.isAfter(vt)) {
                    throw new PhaseFail("CHAIN_CERT_NOT_VALID",
                            "Certificate at index " + i + " is not valid (valid from " + IsoTime.from(vf) + " to " + IsoTime.from(vt) + ")");
                }
            } catch (java.security.cert.CertificateException e) {
                throw new PhaseFail("CHAIN_CERT_NOT_VALID", "Certificate at index " + i + " could not be parsed");
            }
        }
        return leafCertValidityWarning;
    }

    private static String verifyChainSignaturesAndRoot(List<byte[]> chain) {
        byte[] root = chain.get(chain.size() - 1);
        if (!CertUtils.matchesPinnedCA(root, GoogleRoots.GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS)) {
            throw new PhaseFail("ROOT_CA_MISMATCH", "Certificate chain verification failed: root CA does not match any pinned CA");
        }
        if (!CertUtils.validateCertificatePath(chain, GoogleRoots.GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS)) {
            throw new PhaseFail("CHAIN_PATH_INVALID", "Certificate path validation failed");
        }

        try {
            return CertUtils.loadCertificate(root).getSubjectX500Principal().getName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static void verifyHardwareAttestationSecurityLevel(byte[] chain0Der) {
        KeymasterExt.KeyDescription description = KeymasterExt.parseKeyDescription(chain0Der);
        if (description == null) {
            throw new PhaseFail("KEYMASTER_EXT_MISSING",
                    "Android Key Attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) missing or unparseable on leaf attestation cert");
        }
        if (!KeymasterExt.isHardwareAttestationSecurityLevel(description)) {
            throw new PhaseFail("ATTESTATION_SECURITY_LEVEL_INVALID",
                    "Android Key Attestation verification failed: attestationSecurityLevel must be TrustedEnvironment or StrongBox");
        }
    }

    private static void verifyChainRevocations(byte[] leaf, List<byte[]> chain, AttestationLogger logger) {
        Revocation.StatusList statusList = Revocation.fetchRevocationStatusList(logger);

        Revocation.RevocationResult leafRevocation = Revocation.checkCertificateRevocation(leaf, statusList);
        if (leafRevocation.isRevoked()) {
            throw new PhaseFail("LEAF_REVOKED", "Leaf certificate has been " + leafRevocation.status() + ": " + leafRevocation.reason());
        }

        for (int i = 0; i < chain.size(); i++) {
            Revocation.RevocationResult revocation = Revocation.checkCertificateRevocation(chain.get(i), statusList);
            if (revocation.isRevoked()) {
                throw new PhaseFail("CHAIN_CERT_REVOKED", "Certificate at index " + i + " has been " + revocation.status() + ": " + revocation.reason());
            }
        }
    }

    private static String verifyChainBindsSessionChallenge(byte[] chain0Der, String sessionChallengeHash) {
        if (sessionChallengeHash == null || sessionChallengeHash.isEmpty()) {
            throw new PhaseFail("MISSING_CHALLENGE_HASH", "Missing challengeHash in message data");
        }
        if (sessionChallengeHash.length() % 2 != 0 || !isHex(sessionChallengeHash)) {
            throw new PhaseFail("CHALLENGE_HASH_FORMAT", "challengeHash must be a hex string of even length");
        }
        byte[] sessionChallengeBytes = HexUtils.fromHex(sessionChallengeHash);
        byte[] chainChallenge = KeymasterExt.extractAttestationChallengeFromCert(chain0Der);
        if (chainChallenge == null) {
            throw new PhaseFail("KEYMASTER_EXT_MISSING",
                    "Android Key Attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) missing or unparseable on leaf attestation cert");
        }
        if (!Arrays.equals(chainChallenge, sessionChallengeBytes)) {
            throw new PhaseFail("CHAIN_CHALLENGE_MISMATCH", "Android Key Attestation chain challenge does not match session challengeHash");
        }
        return HexUtils.toHex(chainChallenge);
    }

    private static void throwIfFailed(IntegrityChecks.IntegrityCheckResult result) {
        if (!result.ok()) {
            throw new PhaseFail(result.reason(), result.message());
        }
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private AndroidVerification() {
    }
}
