package com.azure.ai.vision.face.deviceattestation.android;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.azure.ai.vision.face.deviceattestation.Json;
import com.azure.ai.vision.face.deviceattestation.JsonData;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.crypto.CertUtils;
import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.logging.DependencyTelemetry;

/**
 * Google Android Key Attestation certificate revocation list, fetched lazily
 * and cached in-process. {@link #checkCertificateRevocation} fails closed: if
 * the list can't be fetched, every cert is treated as revoked.
 * https://android.googleapis.com/attestation/status
 */
public final class Revocation {

    private static final String REVOCATION_STATUS_URL = "https://android.googleapis.com/attestation/status";
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Object CACHE_LOCK = new Object();

    private static StatusList cache;
    private static Instant fetchedAt;
    private static Duration ttl = Duration.ofHours(1);

    /** The parsed revocation entries (serial-hex -> {status, reason}). */
    public record StatusList(Map<String, Object> entries) {
    }

    /** Whether a certificate is revoked, plus the status/reason when it is. */
    public record RevocationResult(boolean isRevoked, String status, String reason) {
    }

    @SuppressWarnings("unchecked")
    public static StatusList fetchRevocationStatusList(AttestationLogger logger) {
        synchronized (CACHE_LOCK) {
            if (cache != null && Duration.between(fetchedAt, Instant.now()).compareTo(ttl) < 0) {
                return cache;
            }
        }

        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(REVOCATION_STATUS_URL)).GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            if (response.statusCode() != 200) {
                logger.trackDependency(dependency(durationMs, false, String.valueOf(response.statusCode()), 0));
                return null;
            }

            response.headers().firstValue("cache-control").ifPresent(cc -> {
                Matcher m = MAX_AGE.matcher(cc);
                if (m.find()) {
                    ttl = Duration.ofSeconds(Long.parseLong(m.group(1)));
                }
            });

            Map<String, Object> root = Json.parseObject(response.body());
            Map<String, Object> entries = root != null && root.get("entries") instanceof Map
                    ? (Map<String, Object>) root.get("entries")
                    : null;
            StatusList statusList = new StatusList(entries);

            synchronized (CACHE_LOCK) {
                cache = statusList;
                fetchedAt = Instant.now();
            }

            logger.trackDependency(dependency(durationMs, true, "200", entries != null ? entries.size() : 0));
            return statusList;
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            logger.trackDependency(dependency(durationMs, false, "exception", 0));
            logger.trackException(e, Maps.of("source", "fetchRevocationStatusList"));
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static RevocationResult checkCertificateRevocation(byte[] certDer, StatusList statusList) {
        if (statusList == null || statusList.entries() == null) {
            // Fail closed: an attestation we can't check against revocations is rejected.
            return new RevocationResult(true, "REVOKED", "REVOCATION_CHECK_UNAVAILABLE");
        }

        try {
            X509Certificate cert = CertUtils.loadCertificate(certDer);
            String serialHex = cert.getSerialNumber().toString(16);
            Object entry = statusList.entries().get(serialHex);
            if (entry instanceof Map) {
                Map<String, Object> e = (Map<String, Object>) entry;
                String reason = JsonData.getString(e, "reason");
                return new RevocationResult(true, JsonData.getString(e, "status"), reason != null ? reason : "UNSPECIFIED");
            }
            return new RevocationResult(false, null, null);
        } catch (Exception e) {
            return new RevocationResult(false, null, null);
        }
    }

    private static DependencyTelemetry dependency(long durationMs, boolean success, String resultCode, int entryCount) {
        return DependencyTelemetry.builder("AndroidAttestation.RevocationList")
                .target("android.googleapis.com")
                .data(REVOCATION_STATUS_URL)
                .durationMillis(durationMs)
                .success(success)
                .resultCode(resultCode)
                .properties(success ? Maps.of("entryCount", entryCount) : null)
                .build();
    }

    private Revocation() {
    }
}
