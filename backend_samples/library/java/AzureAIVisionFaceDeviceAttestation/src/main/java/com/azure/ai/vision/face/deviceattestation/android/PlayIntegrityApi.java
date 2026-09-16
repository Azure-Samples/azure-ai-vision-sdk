package com.azure.ai.vision.face.deviceattestation.android;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.logging.DependencyTelemetry;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.playintegrity.v1.PlayIntegrity;
import com.google.api.services.playintegrity.v1.model.DecodeIntegrityTokenRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

/**
 * The network/credentials boundary for Google's Play Integrity API. Reads the
 * service-account JSON + package name from config and decodes a client-supplied
 * integrity token into an {@link IntegrityVerdictResult}.
 */
public final class PlayIntegrityApi {

    private static final String SCOPE = "https://www.googleapis.com/auth/playintegrity";

    /**
     * Outcome of a Play Integrity decode attempt. {@code tolerable} is true only for
     * the Google-unavailable (network / HTTP 5xx) or quota-exceeded (HTTP 429)
     * conditions the {@code allowAndroidAttestationWhenGoogleUnavailable} policy may
     * accept; every other failure fails closed.
     */
    public static final class IntegrityVerdictResult {
        public final boolean ok;
        public final PlayIntegrityVerdict verdict;
        public final boolean tolerable;
        public final String reason;

        private IntegrityVerdictResult(boolean ok, PlayIntegrityVerdict verdict, boolean tolerable, String reason) {
            this.ok = ok;
            this.verdict = verdict;
            this.tolerable = tolerable;
            this.reason = reason;
        }

        static IntegrityVerdictResult success(PlayIntegrityVerdict verdict) {
            return new IntegrityVerdictResult(true, verdict, false, "ok");
        }

        static IntegrityVerdictResult failTolerable(String reason) {
            return new IntegrityVerdictResult(false, null, true, reason);
        }

        static IntegrityVerdictResult failHard(String reason) {
            return new IntegrityVerdictResult(false, null, false, reason);
        }
    }

    public static IntegrityVerdictResult decryptAndVerifyIntegrityVerdict(
            AttestationConfig config,
            AttestationLogger logger,
            String integrityToken) {
        String serviceAccountJson = config.googleServiceAccountJson();
        if (serviceAccountJson == null || serviceAccountJson.isEmpty()) {
            logger.trackEvent("AndroidAuth.PlayIntegrity.ConfigMissing", Maps.of("missing", "GOOGLE_SERVICE_ACCOUNT_JSON"), null);
            return IntegrityVerdictResult.failHard("service_account_not_configured");
        }
        String packageName = config.androidPackageName();
        if (packageName == null || packageName.isEmpty()) {
            logger.trackEvent("AndroidAuth.PlayIntegrity.ConfigMissing", Maps.of("missing", "ANDROID_PACKAGE_NAME"), null);
            return IntegrityVerdictResult.failHard("package_name_not_configured");
        }

        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(List.of(SCOPE));
        } catch (Exception e) {
            logger.trackException(e, Maps.of("source", "decryptAndVerifyIntegrityVerdict.parseCreds"));
            return IntegrityVerdictResult.failHard("service_account_parse_error");
        }

        long start = System.nanoTime();
        try {
            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            PlayIntegrity service = new PlayIntegrity.Builder(transport, GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(credentials))
                    .setApplicationName("AzureAIVisionFaceDeviceAttestation")
                    .build();
            DecodeIntegrityTokenRequest request = new DecodeIntegrityTokenRequest().setIntegrityToken(integrityToken);
            var response = service.v1().decodeIntegrityToken(packageName, request).execute();
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            if (response == null || response.getTokenPayloadExternal() == null) {
                logger.trackDependency(dependency(packageName, durationMs, false, "empty-response"));
                return IntegrityVerdictResult.failHard("empty_response");
            }

            PlayIntegrityVerdict verdict = map(response.getTokenPayloadExternal());
            logger.trackDependency(dependency(packageName, durationMs, true, "200"));
            return IntegrityVerdictResult.success(verdict);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            logger.trackDependency(dependency(packageName, durationMs, false, "exception"));
            logger.trackException(e, Maps.of("source", "decryptAndVerifyIntegrityVerdict", "packageName", packageName));
            String kind = classifyUnavailable(e);
            return kind != null ? IntegrityVerdictResult.failTolerable(kind) : IntegrityVerdictResult.failHard("api_error");
        }
    }

    /**
     * Classify a decode error as a tolerable Google-unavailability condition
     * ("server_unavailable" for a network error / HTTP 5xx, "quota_exceeded" for
     * HTTP 429) or null when it must fail closed.
     */
    private static String classifyUnavailable(Exception e) {
        if (e instanceof HttpResponseException hre) {
            int status = hre.getStatusCode();
            if (status == 429) {
                return "quota_exceeded";
            }
            if (status >= 500 && status <= 599) {
                return "server_unavailable";
            }
            return null;
        }
        if (e instanceof java.net.UnknownHostException
                || e instanceof java.net.ConnectException
                || e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.NoRouteToHostException) {
            return "server_unavailable";
        }
        return null;
    }

    private static DependencyTelemetry dependency(String packageName, long durationMs, boolean success, String resultCode) {
        return DependencyTelemetry.builder("PlayIntegrity.decodeIntegrityToken")
                .target("playintegrity.googleapis.com")
                .data("packageName=" + packageName)
                .durationMillis(durationMs)
                .success(success)
                .resultCode(resultCode)
                .properties(Maps.of("packageName", packageName))
                .build();
    }

    private static PlayIntegrityVerdict map(com.google.api.services.playintegrity.v1.model.TokenPayloadExternal t) {
        PlayIntegrityVerdict verdict = new PlayIntegrityVerdict();

        var rd = t.getRequestDetails();
        if (rd != null) {
            PlayIntegrityVerdict.RequestDetails d = new PlayIntegrityVerdict.RequestDetails();
            d.requestPackageName = rd.getRequestPackageName();
            d.timestampMillis = rd.getTimestampMillis() != null ? String.valueOf(rd.getTimestampMillis()) : null;
            d.requestHash = rd.getRequestHash();
            verdict.requestDetails = d;
        }

        var ad = t.getAccountDetails();
        if (ad != null) {
            PlayIntegrityVerdict.AccountDetails d = new PlayIntegrityVerdict.AccountDetails();
            d.appLicensingVerdict = ad.getAppLicensingVerdict();
            verdict.accountDetails = d;
        }

        var ai = t.getAppIntegrity();
        if (ai != null) {
            PlayIntegrityVerdict.AppIntegrity d = new PlayIntegrityVerdict.AppIntegrity();
            d.appRecognitionVerdict = ai.getAppRecognitionVerdict();
            d.packageName = ai.getPackageName();
            d.certificateSha256Digest = ai.getCertificateSha256Digest();
            d.versionCode = ai.getVersionCode() != null ? String.valueOf(ai.getVersionCode()) : null;
            verdict.appIntegrity = d;
        }

        var di = t.getDeviceIntegrity();
        if (di != null) {
            PlayIntegrityVerdict.DeviceIntegrity d = new PlayIntegrityVerdict.DeviceIntegrity();
            d.deviceRecognitionVerdict = di.getDeviceRecognitionVerdict();
            var rda = di.getRecentDeviceActivity();
            if (rda != null) {
                PlayIntegrityVerdict.RecentDeviceActivity r = new PlayIntegrityVerdict.RecentDeviceActivity();
                r.deviceActivityLevel = rda.getDeviceActivityLevel();
                d.recentDeviceActivity = r;
            }
            verdict.deviceIntegrity = d;
        }

        var ed = t.getEnvironmentDetails();
        if (ed != null) {
            PlayIntegrityVerdict.EnvironmentDetails d = new PlayIntegrityVerdict.EnvironmentDetails();
            d.playProtectVerdict = ed.getPlayProtectVerdict();
            var arv = ed.getAppAccessRiskVerdict();
            if (arv != null) {
                PlayIntegrityVerdict.AppAccessRiskVerdict a = new PlayIntegrityVerdict.AppAccessRiskVerdict();
                a.appsDetected = arv.getAppsDetected();
                d.appAccessRiskVerdict = a;
            }
            verdict.environmentDetails = d;
        }

        return verdict;
    }

    private PlayIntegrityApi() {
    }
}
