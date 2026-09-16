package com.azure.ai.vision.face.sample.service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import com.azure.ai.vision.face.sample.config.AppSettings;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Landing-page helpers: platform detection, QR generation, and store URLs. */
@Component
public final class SessionLanding {

    /** URLs and button metadata for a session landing page. */
    public record LandingLinks(String nativeUrl, String resultUrl, String qrUrl, String actionUrl, String actionLabel) {
    }

    private final AppSettings settings;

    public SessionLanding(AppSettings settings) {
        this.settings = settings;
    }

    /** Classify the caller from its User-Agent. */
    public String detectPlatform(String userAgent) {
        if (userAgent == null) {
            return "desktop";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("android")) {
            return "android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "ios";
        }
        if (ua.contains("macintosh") && ua.contains("mobile")) {
            return "ios";
        }
        return "desktop";
    }

    /** Render a PNG QR code for {@code url} as a data URI. */
    public String buildQrDataUri(String url) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 240, 240);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix), "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    /** Build the same callback-aware launch URLs as the Node backend. */
    public LandingLinks buildLinks(String origin, String sessionIdParam, String sessionId, String platform) {
        String normalizedOrigin = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        String nativeUrl = appendQuery(normalizedOrigin + "/native", Map.of(sessionIdParam, sessionId));
        String resultUrl = appendQuery(normalizedOrigin + "/result", Map.of(sessionIdParam, sessionId));
        String launchUrl = appendQuery(nativeUrl, Map.of("callbackUrl", resultUrl));

        String actionUrl = "";
        String actionLabel = "";
        if ("android".equals(platform)) {
            String packageName = trim(settings.getAndroidPackageName());
            String playStoreUrl = trim(settings.getAndroidPlayStoreUrl());
            String fallbackUrl = playStoreUrl.isEmpty()
                    ? launchUrl
                    : appendQuery(playStoreUrl, Map.of("referrer", launchUrl));
            actionUrl = packageName.isEmpty()
                    ? launchUrl
                    : androidIntentUrl(launchUrl, packageName, fallbackUrl);
            actionLabel = "Open in app";
        } else if ("ios".equals(platform)) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put(sessionIdParam, sessionId);
            params.put("callbackUrl", resultUrl);
            params.put("domain", host(origin));
            actionUrl = appendQuery(trim(settings.getIosAppStoreUrl()), params);
            actionLabel = "Open App Clip";
        }

        return new LandingLinks(nativeUrl, resultUrl, launchUrl, actionUrl, actionLabel);
    }

    private static String appendQuery(String url, Map<String, String> params) {
        if (url == null || url.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder(url);
        String separator = url.contains("?") ? "&" : "?";
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            result.append(separator)
                    .append(encode(entry.getKey()))
                    .append('=')
                    .append(encode(entry.getValue()));
            separator = "&";
        }
        return result.toString();
    }

    private static String androidIntentUrl(String httpsUrl, String packageName, String fallbackUrl) {
        URI uri = URI.create(httpsUrl);
        StringBuilder target = new StringBuilder(uri.getRawAuthority()).append(uri.getRawPath());
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            target.append('?').append(uri.getRawQuery());
        }
        return "intent://" + target + "#Intent;scheme=https;package=" + packageName
                + ";S.browser_fallback_url=" + encode(fallbackUrl) + ";end";
    }

    private static String host(String origin) {
        try {
            String host = URI.create(origin).getHost();
            return host != null ? host : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String encode(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
