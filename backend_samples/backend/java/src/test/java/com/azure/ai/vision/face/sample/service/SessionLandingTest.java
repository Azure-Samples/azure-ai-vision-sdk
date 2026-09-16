package com.azure.ai.vision.face.sample.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.azure.ai.vision.face.sample.config.AppSettings;
import com.azure.ai.vision.face.sample.service.SessionLanding.LandingLinks;

class SessionLandingTest {

    private static final String SESSION_ID = "11111111-2222-3333-4444-555555555555";
    private static final String ORIGIN = "https://liveness.example.com";
    private static final String RESULT_URL = ORIGIN + "/result?s=" + SESSION_ID;
    private static final String CALLBACK = "callbackUrl=https%3A%2F%2Fliveness.example.com%2Fresult%3Fs%3D" + SESSION_ID;

    @Test
    void detectsDesktopStyleIpadosUserAgentAsIos() {
        SessionLanding landing = new SessionLanding(new AppSettings());

        assertThat(landing.detectPlatform(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 Mobile/15E148"))
                .isEqualTo("ios");
    }

    @Test
    void queryValuesUsePercentEncodingInsteadOfFormEncoding() {
        SessionLanding landing = new SessionLanding(new AppSettings());

        LandingLinks links = landing.buildLinks(ORIGIN, "s", "session + one", "desktop");

        assertThat(links.nativeUrl()).isEqualTo(ORIGIN + "/native?s=session%20%2B%20one");
    }

    @Test
    void iosLinksCarrySessionCallbackAndBackendDomain() {
        AppSettings settings = new AppSettings();
        settings.setIosAppStoreUrl("https://appclip.apple.com/id?p=com.example.Clip");
        SessionLanding landing = new SessionLanding(settings);

        LandingLinks links = landing.buildLinks(ORIGIN, "s", SESSION_ID, "ios");

        assertThat(links.nativeUrl()).isEqualTo(ORIGIN + "/native?s=" + SESSION_ID);
        assertThat(links.resultUrl()).isEqualTo(RESULT_URL);
        assertThat(links.qrUrl()).isEqualTo(ORIGIN + "/native?s=" + SESSION_ID + "&" + CALLBACK);
        assertThat(links.actionUrl()).isEqualTo(
                "https://appclip.apple.com/id?p=com.example.Clip&s=" + SESSION_ID + "&" + CALLBACK
                        + "&domain=liveness.example.com");
        assertThat(links.actionLabel()).isEqualTo("Open App Clip");
    }

    @Test
    void androidIntentCarriesCallbackAndPlayFallback() {
        AppSettings settings = new AppSettings();
        settings.setAndroidPackageName("com.example.liveness");
        settings.setAndroidPlayStoreUrl("https://play.google.com/store/apps/details?id=com.example.liveness");
        SessionLanding landing = new SessionLanding(settings);

        LandingLinks links = landing.buildLinks(ORIGIN, "s", SESSION_ID, "android");

        assertThat(links.actionUrl())
                .startsWith("intent://liveness.example.com/native?s=" + SESSION_ID + "&" + CALLBACK)
                .contains("#Intent;scheme=https;package=com.example.liveness;")
                .contains("S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails")
                .endsWith(";end");
    }
}