package com.azure.ai.vision.face.deviceattestation.services;

import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.JsonData;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.handlers.Outcomes;
import com.azure.ai.vision.face.deviceattestation.ios.IosVerifier;
import com.azure.ai.vision.face.deviceattestation.ios.OngoingAssertionResult;
import com.azure.ai.vision.face.deviceattestation.store.CertificateData;

/**
 * Shared "verify a fresh App Attest assertion + advance the persisted
 * signCount" helper used by every iOS-aware route after registration
 * (attestation/verify, session/token, liveness/digest).
 */
public final class IosAssertionCheck {

    public static IosAssertionCheckResult check(AttestationContext ctx, IosAssertionCheckArgs args) {
        if (args.assertion() == null || args.assertion().isEmpty()) {
            return IosAssertionCheckResult.failure(Outcomes.fail(ctx.logger, args.routeName(), 401, "MISSING_ASSERTION",
                    "Missing iOS App Attest assertion",
                    Maps.of("sid", args.sessionId(), "thumbprint", args.thumbprint()), null, null));
        }

        var snapshot = ctx.store.getCertificate(args.thumbprint());
        CertificateData certRecord = snapshot == null ? null : snapshot.value();
        if (certRecord == null) {
            return IosAssertionCheckResult.failure(Outcomes.fail(ctx.logger, args.routeName(), 401, "CERT_RECORD_MISSING",
                    "Certificate record not found",
                    Maps.of("sid", args.sessionId(), "thumbprint", args.thumbprint()), null, null));
        }

        Map<String, Object> verdict = asMap(certRecord.metadata == null ? null : certRecord.metadata.get("appAttestVerdict"));
        String credCertPem = verdict != null ? JsonData.getString(verdict, "credCertPem") : null;
        if (credCertPem == null || credCertPem.isEmpty()) {
            // Pre-change cert records need re-registration; cert TTL bounds this.
            return IosAssertionCheckResult.failure(Outcomes.fail(ctx.logger, args.routeName(), 401, "LEGACY_CERT_NO_CREDCERT_PEM",
                    "Certificate predates assertion requirement; re-registration required",
                    Maps.of("sid", args.sessionId(), "thumbprint", args.thumbprint()), null, null));
        }

        String expectedRpIdHash = IosVerifier.getExpectedIosRpIdHash(ctx.config);
        if (expectedRpIdHash == null || expectedRpIdHash.isEmpty()) {
            return IosAssertionCheckResult.failure(Outcomes.fail(ctx.logger, args.routeName(), 500, "MISSING_IOS_APP_ID",
                    "Server misconfigured: IOS_APP_ID not set",
                    Maps.of("sid", args.sessionId()), null, null));
        }

        long lastSignCount = resolveLastSignCount(certRecord.metadata, verdict);

        OngoingAssertionResult assertionResult = IosVerifier.verifyIosOngoingAssertion(
                credCertPem, args.blob(), args.assertion(), expectedRpIdHash, lastSignCount);

        if (!assertionResult.ok) {
            return IosAssertionCheckResult.failure(Outcomes.fail(ctx.logger, args.routeName(), 401, "ASSERTION_VERIFY_FAIL",
                    "iOS assertion verification failed",
                    Maps.of(
                            "sid", args.sessionId(),
                            "thumbprint", args.thumbprint(),
                            "reason", assertionResult.reason,
                            "message", assertionResult.message,
                            "signCount", assertionResult.signCount,
                            "lastSignCount", lastSignCount),
                    null, null));
        }

        return IosAssertionCheckResult.success(() -> CertStore.updateCertificateMetadata(ctx, args.thumbprint(),
            Maps.of("lastAssertionSignCount", assertionResult.signCount), snapshot));
    }

    private static long resolveLastSignCount(Map<String, Object> metadata, Map<String, Object> verdict) {
        if (metadata != null) {
            Long persisted = JsonData.getLong(metadata, "lastAssertionSignCount");
            if (persisted != null) {
                return persisted;
            }
        }
        Map<String, Object> assertion = verdict == null ? null : asMap(verdict.get("assertion"));
        if (assertion != null) {
            Long fromVerdict = JsonData.getLong(assertion, "signCount");
            if (fromVerdict != null) {
                return fromVerdict;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private IosAssertionCheck() {
    }
}
