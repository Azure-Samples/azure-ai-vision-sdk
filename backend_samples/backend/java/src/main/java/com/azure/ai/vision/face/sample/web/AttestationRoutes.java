package com.azure.ai.vision.face.sample.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.azure.ai.vision.face.deviceattestation.AttestationService;
import com.azure.ai.vision.face.deviceattestation.LivenessOutcome;
import com.azure.ai.vision.face.deviceattestation.handlers.HandlerOutcome;
import com.azure.ai.vision.face.deviceattestation.models.AttestationChallengeRequest;
import com.azure.ai.vision.face.deviceattestation.models.AttestationRegisterBody;
import com.azure.ai.vision.face.deviceattestation.models.AttestationRegisterRequest;
import com.azure.ai.vision.face.deviceattestation.models.AttestationVerifyBody;
import com.azure.ai.vision.face.deviceattestation.models.AttestationVerifyRequest;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestBody;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestRequest;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenBody;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenRequest;
import com.azure.ai.vision.face.sample.config.ApiRoutes;
import com.azure.ai.vision.face.sample.service.FaceLivenessApi;
import com.azure.ai.vision.face.sample.store.AppSession;
import com.azure.ai.vision.face.sample.store.AppSessionStore;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Maps the configurable HTTP routes onto the library's transport-agnostic
 * {@link AttestationService}. All attestation logic lives in the library; this
 * just parses requests and serializes the {@link HandlerOutcome}.
 */
@Configuration
public class AttestationRoutes {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final ApiRoutes routes;
    private final AttestationService svc;
    private final AppSessionStore appSessions;
    private final FaceLivenessApi face;

    public AttestationRoutes(ApiRoutes routes, AttestationService svc, AppSessionStore appSessions, FaceLivenessApi face) {
        this.routes = routes;
        this.svc = svc;
        this.appSessions = appSessions;
        this.face = face;
    }

    @Bean
    public RouterFunction<ServerResponse> attestationRouter() {
        return RouterFunctions.route()
                .POST(routes.getChallenge(), this::challenge)
                .POST(routes.getRegister(), this::register)
                .POST(routes.getVerify(), this::verify)
                .POST(routes.getSessionToken(), this::sessionToken)
                .POST(routes.getLivenessDigest(), this::livenessDigest)
                .GET(routes.getSessionResult(), this::sessionResult)
                .GET(routes.getAppleAppSiteAssociation(), this::appleAppSiteAssociation)
                .GET(routes.getAssetLinks(), this::assetLinks)
                .GET(routes.getHealthz(), this::healthz)
                .build();
    }

    private ServerResponse challenge(ServerRequest req) {
        return json(svc.challenge(new AttestationChallengeRequest(
                param(req, routes.getSessionIdParam()),
                param(req, routes.getClientIdParam()),
                param(req, routes.getSystemParam()))));
    }

    private ServerResponse register(ServerRequest req) {
        AttestationRegisterBody body = readBody(req, AttestationRegisterBody.class);
        return json(svc.register(new AttestationRegisterRequest(
                param(req, routes.getSessionIdParam()),
                param(req, routes.getClientIdParam()),
                param(req, routes.getSystemParam()),
                body)));
    }

    private ServerResponse verify(ServerRequest req) {
        AttestationVerifyBody body = readBody(req, AttestationVerifyBody.class);
        return json(svc.verify(new AttestationVerifyRequest(
                param(req, routes.getSessionIdParam()),
                param(req, routes.getClientIdParam()),
                param(req, routes.getSystemParam()),
                body)));
    }

    private ServerResponse sessionToken(ServerRequest req) {
        SessionTokenBody body = readBody(req, SessionTokenBody.class);
        return json(svc.sessionToken(new SessionTokenRequest(param(req, routes.getSessionIdParam()), body)));
    }

    private ServerResponse livenessDigest(ServerRequest req) {
        LivenessDigestBody body = readBody(req, LivenessDigestBody.class);
        return json(svc.livenessDigest(new LivenessDigestRequest(param(req, routes.getSessionIdParam()), body)));
    }

    private ServerResponse sessionResult(ServerRequest req) {
        String sid = req.param(routes.getSessionIdParam()).orElse("");
        if (sid.isEmpty()) {
            return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("message", "Missing session ID"));
        }
        AppSession app = appSessions.get(sid);
        if (app == null) {
            return ServerResponse.status(404).contentType(MediaType.APPLICATION_JSON).body(Map.of("status", "notfound"));
        }

        LivenessOutcome outcome = svc.getLivenessOutcome(sid);
        if (!outcome.completed) {
            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(Map.of("status", "pending"));
        }

        try {
            JsonNode result = face.querySessionResult(app.resource(), app.apiKey(), app.action(), sid);
            JsonNode attempt = result.path("results").path("attempts").path(0).path("result");
            if (attempt.path("livenessDecision").asText("").isEmpty()) {
                return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(Map.of("status", "pending"));
            }
            JsonNode serviceDigest = attempt.path("digest");
            if (outcome.clientDigest == null || outcome.clientDigest.isBlank()
                    || !serviceDigest.isTextual() || !outcome.clientDigest.equals(serviceDigest.textValue())) {
                return ServerResponse.status(409).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("status", "error", "code", "DIGEST_MISMATCH",
                                "message", "Liveness result digest validation failed"));
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "done");
            response.put("result", JSON.convertValue(result, Object.class));
            response.put("clientDigest", outcome.clientDigest);
            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (FaceLivenessApi.FaceApiException e) {
            return ServerResponse.status(502).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", "error", "message", "Result query failed (HTTP " + e.status + ")"));
        }
    }

    private ServerResponse appleAppSiteAssociation(ServerRequest req) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(svc.appleAppSiteAssociation());
    }

    private ServerResponse assetLinks(ServerRequest req) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(svc.androidAssetLinks());
    }

    private ServerResponse healthz(ServerRequest req) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(Map.of("status", "ok"));
    }

    private static ServerResponse json(HandlerOutcome outcome) {
        return ServerResponse.status(outcome.status).contentType(MediaType.APPLICATION_JSON).body(outcome.body);
    }

    private static String param(ServerRequest req, String name) {
        return req.param(name).orElse(null);
    }

    private static <T> T readBody(ServerRequest req, Class<T> type) {
        try {
            return req.body(type);
        } catch (Exception e) {
            return null;
        }
    }
}
