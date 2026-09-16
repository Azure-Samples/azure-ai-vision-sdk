package com.azure.ai.vision.face.deviceattestation.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;

/**
 * App Link / Universal Link binding documents served at /.well-known/*, driven
 * entirely by the injected {@link AttestationConfig} so the library binds to any
 * domain / app identity purely through configuration.
 */
public final class WellKnown {

    /** Build the AASA document iOS fetches from /.well-known to bind the domain. */
    public static Map<String, Object> appleAppSiteAssociation(AttestationConfig cfg) {
        String applink = cfg.iosApplinkAppId();
        String appId = ((applink != null && !applink.isEmpty()) ? applink : cfg.iosAppId()).trim();
        String clipId = (cfg.iosAppClipId() == null ? "" : cfg.iosAppClipId()).trim();

        List<Object> appIds = new ArrayList<>();
        if (!appId.isEmpty()) {
            appIds.add(appId);
        }
        if (!clipId.isEmpty()) {
            appIds.add(clipId);
        }

        Map<String, Object> component = Maps.of("/", cfg.applinkPath(), "comment", "Opens the app for liveness sessions.");
        Map<String, Object> detail = Maps.of("appIDs", appIds, "components", List.of(component));
        Map<String, Object> applinks = Maps.of("details", List.of(detail));

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("applinks", applinks);
        if (!clipId.isEmpty()) {
            doc.put("appclips", Maps.of("apps", List.of(clipId)));
        }
        return doc;
    }

    /** Build the Digital Asset Links document Android fetches to bind the domain. */
    public static List<Object> assetLinks(AttestationConfig cfg) {
        String packageName = (cfg.androidPackageName() == null ? "" : cfg.androidPackageName()).trim();
        List<Object> fingerprints = new ArrayList<>(cfg.androidSha256CertFingerprints());

        Map<String, Object> target = Maps.of(
                "namespace", "android_app",
                "package_name", packageName,
                "sha256_cert_fingerprints", fingerprints);
        Map<String, Object> entry = Maps.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", target);

        List<Object> result = new ArrayList<>();
        result.add(entry);
        return result;
    }

    private WellKnown() {
    }
}
