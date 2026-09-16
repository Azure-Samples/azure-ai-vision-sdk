package com.azure.ai.vision.face.deviceattestation.ios;

import java.nio.charset.StandardCharsets;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.crypto.HashUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.HexUtils;
import com.azure.ai.vision.face.deviceattestation.services.AttestationMessageData;
import com.azure.ai.vision.face.deviceattestation.services.AuthVerificationResult;

/**
 * iOS App Attest verifier — the seam the handlers depend on. Delegates the
 * attestation + assertion logic to {@link AppAttestVerification}.
 */
public final class IosVerifier {

    /** Verify an App Attest attestation (registration). */
    public static AuthVerificationResult verify(AttestationContext ctx, AttestationMessageData messageData, String attestJson) {
        return AppAttestVerification.verify(ctx.config, ctx.logger, messageData, attestJson);
    }

    /** Expected rpIdHash: SHA-256 of the configured iOS App ID (lowercase hex). */
    public static String getExpectedIosRpIdHash(AttestationConfig config) {
        if (config.iosAppId() == null || config.iosAppId().isEmpty()) {
            return null;
        }
        return HexUtils.toHex(HashUtils.sha256(config.iosAppId().getBytes(StandardCharsets.UTF_8)));
    }

    /** Verify a fresh per-call assertion against the persisted credCert. */
    public static OngoingAssertionResult verifyIosOngoingAssertion(
            String credCertPem,
            byte[] blob,
            String assertion,
            String expectedRpIdHash,
            long lastSignCount) {
        return AppAttestVerification.verifyOngoingAssertion(credCertPem, blob, assertion, expectedRpIdHash, lastSignCount);
    }

    private IosVerifier() {
    }
}
