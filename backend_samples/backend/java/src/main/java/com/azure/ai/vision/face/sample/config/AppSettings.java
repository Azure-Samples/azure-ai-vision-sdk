package com.azure.ai.vision.face.sample.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;

/**
 * Strongly-typed application settings bound from {@code application.yml} (which
 * maps the deployment environment variables). {@link #toAttestationConfig()}
 * produces the library's {@link AttestationConfig}.
 */
@ConfigurationProperties(prefix = "app")
public class AppSettings {

    // --- Attestation policy (mapped to AttestationConfig) ---
    private int maxCertSize = 10240;
    private String androidPackageName = "";
    private String googleServiceAccountJson = "";
    private boolean debugMode = false;
    private boolean allowDeviceIntegrity = false;
    private boolean allowBasicIntegrity = false;
    private boolean allowAndroidAttestationWhenGoogleUnavailable = false;
    private String iosAppId = "";
    private String iosApplinkAppId = "";
    private String iosAppClipId = "";
    private String androidSha256CertFingerprints = "";
    private String applinkPath = "/native*";

    // --- App-owned settings ---
    private long sessionTokenTtl = 600;
    private long certTtl = 604800;
    private String faceApiVersion = "v1.2";
    private boolean useLocalRedis = false;
    private String redisHostname = "";
    private int redisPort = 6380;
    private String iosAppStoreUrl = "";
    private String androidPlayStoreUrl = "";

    /** Split the comma/space-separated Android fingerprints into a list. */
    public List<String> androidFingerprintList() {
        List<String> result = new ArrayList<>();
        if (androidSha256CertFingerprints != null) {
            for (String part : androidSha256CertFingerprints.split("[,\\s]+")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    /** Build the library configuration from these settings. */
    public AttestationConfig toAttestationConfig() {
        return AttestationConfig.builder()
                .maxCertSize(maxCertSize)
                .androidPackageName(androidPackageName)
                .googleServiceAccountJson(googleServiceAccountJson)
                .debugMode(debugMode)
                .allowDeviceIntegrity(allowDeviceIntegrity)
                .allowBasicIntegrity(allowBasicIntegrity)
                .allowAndroidAttestationWhenGoogleUnavailable(allowAndroidAttestationWhenGoogleUnavailable)
                .iosAppId(iosAppId)
                .iosApplinkAppId(emptyToNull(iosApplinkAppId))
                .iosAppClipId(emptyToNull(iosAppClipId))
                .androidSha256CertFingerprints(androidFingerprintList())
                .applinkPath(applinkPath)
                .build();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    public int getMaxCertSize() { return maxCertSize; }
    public void setMaxCertSize(int v) { this.maxCertSize = v; }
    public String getAndroidPackageName() { return androidPackageName; }
    public void setAndroidPackageName(String v) { this.androidPackageName = v; }
    public String getGoogleServiceAccountJson() { return googleServiceAccountJson; }
    public void setGoogleServiceAccountJson(String v) { this.googleServiceAccountJson = v; }
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean v) { this.debugMode = v; }
    public boolean isAllowDeviceIntegrity() { return allowDeviceIntegrity; }
    public void setAllowDeviceIntegrity(boolean v) { this.allowDeviceIntegrity = v; }
    public boolean isAllowBasicIntegrity() { return allowBasicIntegrity; }
    public void setAllowBasicIntegrity(boolean v) { this.allowBasicIntegrity = v; }
    public boolean isAllowAndroidAttestationWhenGoogleUnavailable() { return allowAndroidAttestationWhenGoogleUnavailable; }
    public void setAllowAndroidAttestationWhenGoogleUnavailable(boolean v) { this.allowAndroidAttestationWhenGoogleUnavailable = v; }
    public String getIosAppId() { return iosAppId; }
    public void setIosAppId(String v) { this.iosAppId = v; }
    public String getIosApplinkAppId() { return iosApplinkAppId; }
    public void setIosApplinkAppId(String v) { this.iosApplinkAppId = v; }
    public String getIosAppClipId() { return iosAppClipId; }
    public void setIosAppClipId(String v) { this.iosAppClipId = v; }
    public String getAndroidSha256CertFingerprints() { return androidSha256CertFingerprints; }
    public void setAndroidSha256CertFingerprints(String v) { this.androidSha256CertFingerprints = v; }
    public String getApplinkPath() { return applinkPath; }
    public void setApplinkPath(String v) { this.applinkPath = v; }
    public long getSessionTokenTtl() { return sessionTokenTtl; }
    public void setSessionTokenTtl(long v) { this.sessionTokenTtl = v; }
    public long getCertTtl() { return certTtl; }
    public void setCertTtl(long v) { this.certTtl = v; }
    public String getFaceApiVersion() { return faceApiVersion; }
    public void setFaceApiVersion(String v) { this.faceApiVersion = v; }
    public boolean isUseLocalRedis() { return useLocalRedis; }
    public void setUseLocalRedis(boolean v) { this.useLocalRedis = v; }
    public String getRedisHostname() { return redisHostname; }
    public void setRedisHostname(String v) { this.redisHostname = v; }
    public int getRedisPort() { return redisPort; }
    public void setRedisPort(int v) { this.redisPort = v; }
    public String getIosAppStoreUrl() { return iosAppStoreUrl; }
    public void setIosAppStoreUrl(String v) { this.iosAppStoreUrl = v; }
    public String getAndroidPlayStoreUrl() { return androidPlayStoreUrl; }
    public void setAndroidPlayStoreUrl(String v) { this.androidPlayStoreUrl = v; }
}
