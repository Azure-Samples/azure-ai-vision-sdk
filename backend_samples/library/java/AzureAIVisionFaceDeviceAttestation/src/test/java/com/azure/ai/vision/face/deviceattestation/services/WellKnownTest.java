package com.azure.ai.vision.face.deviceattestation.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;

class WellKnownTest {

    @Test
    @SuppressWarnings("unchecked")
    void aasaIncludesAppIdsAndClips() {
        AttestationConfig cfg = AttestationConfig.builder()
                .iosAppId("TEAMID.com.example")
                .iosAppClipId("TEAMID.com.example.Clip")
                .applinkPath("/native*")
                .build();

        Map<String, Object> doc = WellKnown.appleAppSiteAssociation(cfg);
        Map<String, Object> applinks = (Map<String, Object>) doc.get("applinks");
        List<Object> details = (List<Object>) applinks.get("details");
        Map<String, Object> detail0 = (Map<String, Object>) details.get(0);
        List<Object> appIDs = (List<Object>) detail0.get("appIDs");

        assertTrue(appIDs.contains("TEAMID.com.example"));
        assertTrue(appIDs.contains("TEAMID.com.example.Clip"));

        Map<String, Object> appclips = (Map<String, Object>) doc.get("appclips");
        assertEquals(List.of("TEAMID.com.example.Clip"), appclips.get("apps"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void assetLinksHasPackageAndFingerprints() {
        AttestationConfig cfg = AttestationConfig.builder()
                .androidPackageName("com.example.app")
                .androidSha256CertFingerprints(List.of("AA:BB:CC"))
                .build();

        List<Object> doc = WellKnown.assetLinks(cfg);
        Map<String, Object> entry = (Map<String, Object>) doc.get(0);
        Map<String, Object> target = (Map<String, Object>) entry.get("target");

        assertEquals("android_app", target.get("namespace"));
        assertEquals("com.example.app", target.get("package_name"));
        assertEquals(List.of("AA:BB:CC"), target.get("sha256_cert_fingerprints"));
    }
}
