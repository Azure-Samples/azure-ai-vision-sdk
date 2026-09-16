package com.azure.ai.vision.face.sample.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.azure.ai.vision.face.deviceattestation.AttestationService;
import com.azure.ai.vision.face.sample.config.ApiRoutes;
import com.azure.ai.vision.face.sample.config.AppSettings;
import com.azure.ai.vision.face.sample.service.FaceLivenessApi;
import com.azure.ai.vision.face.sample.service.SessionLanding;
import com.azure.ai.vision.face.sample.service.SessionLanding.LandingLinks;
import com.azure.ai.vision.face.sample.store.AppSession;
import com.azure.ai.vision.face.sample.store.AppSessionStore;

import jakarta.servlet.http.HttpServletRequest;

/** Index form (start a session + config status) and the fallback landing pages. */
@Controller
public class PageController {

    /** A single configuration variable's presence (never its value). */
    public record ConfigVar(String label, boolean required, boolean set) {
    }

    private final AppSettings settings;
    private final ApiRoutes routes;
    private final FaceLivenessApi face;
    private final AttestationService svc;
    private final AppSessionStore appSessions;
    private final SessionLanding landing;

    public PageController(AppSettings settings, ApiRoutes routes, FaceLivenessApi face,
            AttestationService svc, AppSessionStore appSessions, SessionLanding landing) {
        this.settings = settings;
        this.routes = routes;
        this.face = face;
        this.svc = svc;
        this.appSessions = appSessions;
        this.landing = landing;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("config", configStatus());
        return "index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam String resource,
            @RequestParam String apiKey,
            @RequestParam(defaultValue = "Passive") String mode,
            @RequestParam(required = false) MultipartFile verifyImage,
            RedirectAttributes redirect) {
        if (resource == null || resource.isBlank() || apiKey == null || apiKey.isBlank()) {
            redirect.addFlashAttribute("error", "Face resource and API key are required.");
            return "redirect:/";
        }
        try {
            byte[] image = verifyImage != null && !verifyImage.isEmpty() ? verifyImage.getBytes() : null;
            String action = image != null ? "detectLivenessWithVerify" : "detectLiveness";
            String filename = verifyImage != null ? verifyImage.getOriginalFilename() : null;

            FaceLivenessApi.CreateSessionResult session = face.createSession(resource, apiKey, mode, image, filename);
            if (!isSet(session.sessionId()) || !isSet(session.authToken())) {
                redirect.addFlashAttribute("error", "Face service returned an unexpected response.");
                return "redirect:/";
            }

            if (svc.saveSession(session.sessionId(), session.authToken()) == null) {
                redirect.addFlashAttribute("error", "Could not store the session token. Try again.");
                return "redirect:/";
            }
            if (!appSessions.save(session.sessionId(), new AppSession(resource, apiKey, action))) {
                redirect.addFlashAttribute("error", "Could not store the session. Try again.");
                return "redirect:/";
            }
            return "redirect:/native?" + routes.getSessionIdParam() + "=" + session.sessionId();
        } catch (FaceLivenessApi.FaceApiException e) {
            redirect.addFlashAttribute("error", "Face createSession failed (HTTP " + e.status + ").");
            return "redirect:/";
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error creating session: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping({ "/native", "/result" })
    public String session(HttpServletRequest request, Model model) {
        String sid = request.getParameter(routes.getSessionIdParam());
        boolean exists = sid != null && svc.sessionExists(sid);
        model.addAttribute("sessionId", sid);
        model.addAttribute("exists", exists);

        if (exists) {
            String platform = landing.detectPlatform(request.getHeader("User-Agent"));
            LandingLinks links = landing.buildLinks(baseUrl(request), routes.getSessionIdParam(), sid, platform);
            model.addAttribute("platform", platform);
            model.addAttribute("qr", landing.buildQrDataUri(links.qrUrl()));
            model.addAttribute("actionUrl", links.actionUrl());
            model.addAttribute("actionLabel", links.actionLabel());
            model.addAttribute("resultPollUrl", routes.getSessionResult() + "?" + routes.getSessionIdParam() + "=" + sid);
        }
        return "session";
    }

    private List<ConfigVar> configStatus() {
        List<ConfigVar> list = new ArrayList<>();
        list.add(new ConfigVar("IOS_APP_ID", true, isSet(settings.getIosAppId())));
        list.add(new ConfigVar("ANDROID_PACKAGE_NAME", true, isSet(settings.getAndroidPackageName())));
        list.add(new ConfigVar("GOOGLE_SERVICE_ACCOUNT_JSON", true, isSet(settings.getGoogleServiceAccountJson())));
        list.add(new ConfigVar("IOS_APPLINK_APP_ID", false, isSet(settings.getIosApplinkAppId())));
        list.add(new ConfigVar("IOS_APP_CLIP_ID", false, isSet(settings.getIosAppClipId())));
        list.add(new ConfigVar("ANDROID_SHA256_CERT_FINGERPRINTS", false, !settings.androidFingerprintList().isEmpty()));
        list.add(new ConfigVar("IOS_APP_STORE_URL", false, isSet(settings.getIosAppStoreUrl())));
        list.add(new ConfigVar("ANDROID_PLAY_STORE_URL", false, isSet(settings.getAndroidPlayStoreUrl())));
        list.add(new ConfigVar("DEBUG_MODE", false, settings.isDebugMode()));
        list.add(new ConfigVar("ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE", false,
            settings.isAllowAndroidAttestationWhenGoogleUnavailable()));
        return list;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String baseUrl(HttpServletRequest request) {
        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (!isSet(host)) {
            host = request.getHeader("Disguised-Host");
        }
        if (!isSet(host)) {
            host = request.getHeader("Host");
        }
        if (!isSet(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port != 80 && port != 443) {
                host += ":" + port;
            }
        }
        return "https://" + host.trim();
    }

    private static String firstHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }
}
