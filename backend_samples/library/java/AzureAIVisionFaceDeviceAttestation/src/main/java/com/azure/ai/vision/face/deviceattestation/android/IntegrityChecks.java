package com.azure.ai.vision.face.deviceattestation.android;

import java.util.List;

import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.crypto.HashUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.HexUtils;

/** Semantic checks on the decoded Play Integrity verdict. */
public final class IntegrityChecks {

    /** Outcome of an integrity check: ok, or a failure reason + message. */
    public record IntegrityCheckResult(boolean ok, String reason, String message) {
        public static final IntegrityCheckResult SUCCESS = new IntegrityCheckResult(true, null, null);

        public static IntegrityCheckResult fail(String reason, String message) {
            return new IntegrityCheckResult(false, reason, message);
        }
    }

    /** requestHash must equal hex(sha256(leafCertDer)) — binds the token to the auth key. */
    public static IntegrityCheckResult verifyRequestHash(PlayIntegrityVerdict verdict, byte[] leafCertDer) {
        String expected = HexUtils.toHex(HashUtils.sha256(leafCertDer));
        String actual = verdict.requestDetails != null ? verdict.requestDetails.requestHash : null;
        return expected.equals(actual)
                ? IntegrityCheckResult.SUCCESS
                : IntegrityCheckResult.fail("INTEGRITY_REQUEST_HASH_MISMATCH",
                        "Play Integrity verification failed: requestHash mismatch (possible replay attack)");
    }

    /** Timestamps are required and tokens more than 5 minutes from server time are rejected. */
    public static IntegrityCheckResult verifyTimestamp(PlayIntegrityVerdict verdict) {
        String timestampMillis = verdict.requestDetails != null ? verdict.requestDetails.timestampMillis : null;
        if (timestampMillis == null || timestampMillis.isEmpty()) {
            return IntegrityCheckResult.fail("INTEGRITY_TIMESTAMP_INVALID",
                    "Play Integrity verification failed: token timestamp missing or invalid");
        }
        long tokenTimestamp;
        try {
            tokenTimestamp = Long.parseLong(timestampMillis);
        } catch (NumberFormatException e) {
            return IntegrityCheckResult.fail("INTEGRITY_TIMESTAMP_INVALID",
                    "Play Integrity verification failed: token timestamp missing or invalid");
        }
        long now = System.currentTimeMillis();
        return Math.abs(now - tokenTimestamp) > 5L * 60 * 1000
                ? IntegrityCheckResult.fail("INTEGRITY_TIMESTAMP_OLD", "Play Integrity verification failed: token timestamp too old")
                : IntegrityCheckResult.SUCCESS;
    }

    /**
     * Validate appIntegrity (package name + appRecognitionVerdict) and
     * deviceIntegrity (STRONG / DEVICE / BASIC selectable via config), and
     * collect non-blocking environment warnings.
     */
    public static IntegrityCheckResult evaluate(
            PlayIntegrityVerdict verdict,
            AttestationConfig config,
            boolean debugMode,
            List<String> warnings) {
        PlayIntegrityVerdict.AppIntegrity appIntegrity = verdict.appIntegrity;
        PlayIntegrityVerdict.DeviceIntegrity deviceIntegrity = verdict.deviceIntegrity;
        PlayIntegrityVerdict.EnvironmentDetails environmentDetails = verdict.environmentDetails;

        String expectedPackageName = config.androidPackageName();
        if (expectedPackageName == null || expectedPackageName.isEmpty()) {
            return IntegrityCheckResult.fail("MISSING_PACKAGE_NAME_ENV", "ANDROID_PACKAGE_NAME environment variable not set");
        }
        String actualPackageName = appIntegrity != null ? appIntegrity.packageName : null;
        if (!expectedPackageName.equals(actualPackageName)) {
            return IntegrityCheckResult.fail("PACKAGE_NAME_MISMATCH", "Play Integrity verification failed: package name mismatch");
        }

        String appRecognitionVerdict = appIntegrity != null ? appIntegrity.appRecognitionVerdict : null;
        if (!"PLAY_RECOGNIZED".equals(appRecognitionVerdict)) {
            if (debugMode && "UNRECOGNIZED_VERSION".equals(appRecognitionVerdict)) {
                warnings.add("App recognition warning (debug mode): " + appRecognitionVerdict);
            } else {
                return IntegrityCheckResult.fail("APP_NOT_RECOGNIZED",
                        "Play Integrity verification failed: app not recognized (" + appRecognitionVerdict + ")");
            }
        }

        List<String> deviceVerdicts = deviceIntegrity != null && deviceIntegrity.deviceRecognitionVerdict != null
                ? deviceIntegrity.deviceRecognitionVerdict
                : List.of();
        boolean hasStrongIntegrity = deviceVerdicts.contains("MEETS_STRONG_INTEGRITY");
        boolean allowDeviceIntegrity = config.allowDeviceIntegrity() || debugMode;
        boolean hasDeviceIntegrity = allowDeviceIntegrity && deviceVerdicts.contains("MEETS_DEVICE_INTEGRITY");
        boolean allowBasicIntegrity = config.allowBasicIntegrity() || debugMode;
        boolean hasBasicIntegrity = allowBasicIntegrity && deviceVerdicts.contains("MEETS_BASIC_INTEGRITY");

        if (!hasStrongIntegrity && !hasDeviceIntegrity && !hasBasicIntegrity) {
            return IntegrityCheckResult.fail("DEVICE_INTEGRITY_FAIL",
                    "Play Integrity verification failed: device does not meet integrity requirements");
        }

        String debugSuffix = debugMode ? " (debug mode)" : "";
        if (!hasStrongIntegrity && hasDeviceIntegrity) {
            warnings.add("Device integrity warning" + debugSuffix + ": MEETS_DEVICE_INTEGRITY only, not MEETS_STRONG_INTEGRITY");
        } else if (!hasStrongIntegrity && hasBasicIntegrity) {
            warnings.add("Device integrity warning" + debugSuffix + ": MEETS_BASIC_INTEGRITY only, not MEETS_STRONG_INTEGRITY");
        }

        String playProtectVerdict = environmentDetails != null ? environmentDetails.playProtectVerdict : null;
        if (!"NO_ISSUES".equals(playProtectVerdict)) {
            warnings.add("Play Protect verdict: " + playProtectVerdict);
        }

        List<String> appsDetected = environmentDetails != null && environmentDetails.appAccessRiskVerdict != null
                ? environmentDetails.appAccessRiskVerdict.appsDetected
                : null;
        if (appsDetected != null && !appsDetected.isEmpty()) {
            warnings.add("App Access Risk - detected apps: " + String.join(", ", appsDetected));
        }

        return IntegrityCheckResult.SUCCESS;
    }

    private IntegrityChecks() {
    }
}
