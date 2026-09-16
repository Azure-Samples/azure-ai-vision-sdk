package com.azure.ai.vision.face.deviceattestation.config;

import java.util.Collections;
import java.util.List;

/**
 * Attestation runtime configuration. The library is configured ONCE with an
 * {@link AttestationConfig} when the {@code AttestationService} is created, and
 * never reads environment variables itself — so it can be reused in any host
 * that supplies these values. The host sources them (from env, a secret store,
 * etc.) and builds this object with {@link #builder()}.
 */
public final class AttestationConfig {

    private final int maxCertSize;
    private final String androidPackageName;
    private final String googleServiceAccountJson;
    private final boolean debugMode;
    private final boolean allowDeviceIntegrity;
    private final boolean allowBasicIntegrity;
    private final boolean allowAndroidAttestationWhenGoogleUnavailable;
    private final String iosAppId;
    private final String iosApplinkAppId;
    private final String iosAppClipId;
    private final List<String> androidSha256CertFingerprints;
    private final String applinkPath;

    private AttestationConfig(Builder b) {
        this.maxCertSize = b.maxCertSize;
        this.androidPackageName = b.androidPackageName;
        this.googleServiceAccountJson = b.googleServiceAccountJson;
        this.debugMode = b.debugMode;
        this.allowDeviceIntegrity = b.allowDeviceIntegrity;
        this.allowBasicIntegrity = b.allowBasicIntegrity;
        this.allowAndroidAttestationWhenGoogleUnavailable = b.allowAndroidAttestationWhenGoogleUnavailable;
        this.iosAppId = b.iosAppId;
        this.iosApplinkAppId = b.iosApplinkAppId;
        this.iosAppClipId = b.iosAppClipId;
        this.androidSha256CertFingerprints = List.copyOf(b.androidSha256CertFingerprints);
        this.applinkPath = b.applinkPath;
    }

    /** Max accepted client certificate size in bytes (default 10240). */
    public int maxCertSize() { return maxCertSize; }

    /** Expected Android application id. */
    public String androidPackageName() { return androidPackageName; }

    /** Google service-account JSON for the Play Integrity API. */
    public String googleServiceAccountJson() { return googleServiceAccountJson; }

    /** Loosen recognition/integrity requirements for dev/testing. */
    public boolean debugMode() { return debugMode; }

    /** Accept MEETS_DEVICE_INTEGRITY verdicts. */
    public boolean allowDeviceIntegrity() { return allowDeviceIntegrity; }

    /** Accept MEETS_BASIC_INTEGRITY verdicts. */
    public boolean allowBasicIntegrity() { return allowBasicIntegrity; }

    /**
     * Degraded mode: accept Android registration on the hardware Key Attestation chain
     * alone when Google's Play Integrity API is unreachable/quota-limited or the revocation
     * list can't be fetched. Genuine revocations and failed verdicts still fail, and the
     * resulting cert is session-scoped.
     */
    public boolean allowAndroidAttestationWhenGoogleUnavailable() { return allowAndroidAttestationWhenGoogleUnavailable; }

    /** iOS App Attest identity "&lt;TeamID&gt;.&lt;BundleID&gt;". */
    public String iosAppId() { return iosAppId; }

    /** iOS AASA appID; falls back to {@link #iosAppId()} when unset. */
    public String iosApplinkAppId() { return iosApplinkAppId; }

    /** Optional App Clip appID "&lt;TeamID&gt;.&lt;BundleID&gt;.Clip"; null when unset. */
    public String iosAppClipId() { return iosAppClipId; }

    /** Android signing-cert SHA-256 fingerprints for assetlinks. */
    public List<String> androidSha256CertFingerprints() { return androidSha256CertFingerprints; }

    /** Universal/App Link path pattern (default /native*). */
    public String applinkPath() { return applinkPath; }

    public static Builder builder() { return new Builder(); }

    /** Fluent builder for {@link AttestationConfig}. */
    public static final class Builder {
        private int maxCertSize = 10240;
        private String androidPackageName = "";
        private String googleServiceAccountJson = "";
        private boolean debugMode;
        private boolean allowDeviceIntegrity;
        private boolean allowBasicIntegrity;
        private boolean allowAndroidAttestationWhenGoogleUnavailable;
        private String iosAppId = "";
        private String iosApplinkAppId;
        private String iosAppClipId;
        private List<String> androidSha256CertFingerprints = Collections.emptyList();
        private String applinkPath = "/native*";

        public Builder maxCertSize(int v) { this.maxCertSize = v; return this; }
        public Builder androidPackageName(String v) { this.androidPackageName = v == null ? "" : v; return this; }
        public Builder googleServiceAccountJson(String v) { this.googleServiceAccountJson = v == null ? "" : v; return this; }
        public Builder debugMode(boolean v) { this.debugMode = v; return this; }
        public Builder allowDeviceIntegrity(boolean v) { this.allowDeviceIntegrity = v; return this; }
        public Builder allowBasicIntegrity(boolean v) { this.allowBasicIntegrity = v; return this; }
        public Builder allowAndroidAttestationWhenGoogleUnavailable(boolean v) { this.allowAndroidAttestationWhenGoogleUnavailable = v; return this; }
        public Builder iosAppId(String v) { this.iosAppId = v == null ? "" : v; return this; }
        public Builder iosApplinkAppId(String v) { this.iosApplinkAppId = v; return this; }
        public Builder iosAppClipId(String v) { this.iosAppClipId = v; return this; }
        public Builder androidSha256CertFingerprints(List<String> v) {
            this.androidSha256CertFingerprints = v == null ? Collections.emptyList() : v;
            return this;
        }
        public Builder applinkPath(String v) { this.applinkPath = v == null ? "/native*" : v; return this; }

        public AttestationConfig build() { return new AttestationConfig(this); }
    }
}
