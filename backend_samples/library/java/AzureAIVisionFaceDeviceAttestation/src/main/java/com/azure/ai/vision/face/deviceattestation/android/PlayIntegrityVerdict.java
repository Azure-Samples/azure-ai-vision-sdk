package com.azure.ai.vision.face.deviceattestation.android;

import java.util.List;

/**
 * Decoded Play Integrity verdict. Persisted under the certificate metadata so
 * the post-attestation record retains everything decoded from the token.
 * Public fields so the host's JSON serializer emits them (camelCase).
 */
public final class PlayIntegrityVerdict {

    public RequestDetails requestDetails;
    public AccountDetails accountDetails;
    public AppIntegrity appIntegrity;
    public DeviceIntegrity deviceIntegrity;
    public EnvironmentDetails environmentDetails;

    /**
     * Lowercase hex of the attestationChallenge from the leaf's keymaster
     * extension, populated server-side once verified to equal the session
     * challengeHash (audit record of the session binding).
     */
    public String attestationChallenge;

    public static final class RequestDetails {
        public String requestPackageName;
        public String timestampMillis;
        public String requestHash;
    }

    public static final class AccountDetails {
        public String appLicensingVerdict;
    }

    public static final class AppIntegrity {
        public String appRecognitionVerdict;
        public String packageName;
        public List<String> certificateSha256Digest;
        public String versionCode;
    }

    public static final class RecentDeviceActivity {
        public String deviceActivityLevel;
    }

    public static final class DeviceIntegrity {
        public List<String> deviceRecognitionVerdict;
        public RecentDeviceActivity recentDeviceActivity;
    }

    public static final class AppAccessRiskVerdict {
        public List<String> appsDetected;
    }

    public static final class EnvironmentDetails {
        public String playProtectVerdict;
        public AppAccessRiskVerdict appAccessRiskVerdict;
    }
}
