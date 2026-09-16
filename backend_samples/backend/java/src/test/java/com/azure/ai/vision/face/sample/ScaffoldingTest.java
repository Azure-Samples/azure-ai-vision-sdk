package com.azure.ai.vision.face.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.azure.ai.vision.face.deviceattestation.AttestationService;
import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;
import com.azure.ai.vision.face.deviceattestation.store.SessionRecord;
import com.azure.ai.vision.face.sample.service.FaceLivenessApi;
import com.azure.ai.vision.face.sample.store.AppSession;
import com.azure.ai.vision.face.sample.store.AppSessionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Boots the whole application (in-memory stores, no Redis) and exercises the
 * public surface: health, validation, well-known documents, and the app-session
 * poll. Proves the composition root wires config + store + logger + service.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.ios-app-store-url=https://appclip.apple.com/id?p=com.example.Clip")
class ScaffoldingTest {

    private final HttpClient rest = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AttestationService attestation;

    @Autowired
    private ClusterStore store;

    @Autowired
    private AppSessionStore appSessions;

    @MockitoBean
    private FaceLivenessApi face;

    @Test
    void healthzReturnsOk() throws Exception {
        HttpResponse<String> response = get("/healthz", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("ok");
    }

    @Test
    void challengeWithoutParamsIsRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/attestation/challenge"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = rest.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Missing session ID");
    }

    @Test
    void appleAppSiteAssociationIsServed() throws Exception {
        HttpResponse<String> response = get("/.well-known/apple-app-site-association", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("applinks");
    }

    @Test
    void assetLinksIsServed() throws Exception {
        HttpResponse<String> response = get("/.well-known/assetlinks.json", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("delegate_permission/common.handle_all_urls");
    }

    @Test
    void sessionResultForUnknownSessionIsNotFound() throws Exception {
        HttpResponse<String> response = get("/api/session/result?s=not-a-real-session", null);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("notfound");
    }

    @Test
    void iosLandingCarriesResultCallbackInAppClipButton() throws Exception {
        String sid = "11111111-2222-3333-4444-555555555555";
        assertThat(attestation.saveSession(sid, "face-token")).isEqualTo(sid);

        HttpResponse<String> response = get(
                "/native?s=" + sid, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("Open App Clip")
                .contains("callbackUrl=https%3A%2F%2Flocalhost%3A")
                .contains("%2Fresult%3Fs%3D" + sid)
            .contains("domain=localhost")
            .contains("Client digest")
            .contains("Liveness service result (JSON)")
            .contains("id=\"client-digest\"")
            .contains("id=\"service-result\"")
            .contains("data.clientDigest")
            .contains("DIGEST_MISMATCH")
            .contains("Session rejected: the client and service digests are missing or do not match.")
            .contains("response.ok && data.status === 'done'");
    }

    @ParameterizedTest
    @MethodSource("digestCases")
    void sessionResultRequiresMatchingDigests(String clientDigest, Object serviceDigest, boolean completed,
            boolean hasDecision, int expectedStatus, String expectedState) throws Exception {
        String sid = UUID.randomUUID().toString();
        Map<String, Object> sessionData = new LinkedHashMap<>();
        sessionData.put("digestCompleted", completed);
        sessionData.put("digest", clientDigest);
        assertThat(store.setSession(sid, new SessionRecord("test-token", sessionData))).isTrue();
        assertThat(appSessions.save(sid, new AppSession("test-resource", "test-key", "detectLiveness"))).isTrue();

        ObjectMapper json = new ObjectMapper();
        ObjectNode result = json.createObjectNode();
        ObjectNode attempt = result.putObject("results").putArray("attempts").addObject().putObject("result");
        if (hasDecision) attempt.put("livenessDecision", "real");
        if (serviceDigest != null) attempt.set("digest", json.valueToTree(serviceDigest));
        when(face.querySessionResult("test-resource", "test-key", "detectLiveness", sid)).thenReturn(result);

        HttpResponse<String> response = get("/api/session/result?s=" + sid, null);
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo(expectedState);
        verify(face, times(completed ? 1 : 0)).querySessionResult("test-resource", "test-key", "detectLiveness", sid);
        if (expectedState.equals("done")) {
            assertThat(body.path("clientDigest").asText()).isEqualTo(clientDigest);
            assertThat(body.path("result")).isEqualTo(result);
        } else {
            assertThat(body.has("result")).isFalse();
            assertThat(body.has("clientDigest")).isFalse();
            if (expectedState.equals("error")) assertThat(body.path("code").asText()).isEqualTo("DIGEST_MISMATCH");
        }
    }

    private static Stream<Arguments> digestCases() {
        return Stream.of(
                Arguments.of("client-digest", "client-digest", true, true, 200, "done"),
                Arguments.of("client-digest", "other-digest", true, true, 409, "error"),
                Arguments.of("client-digest", "CLIENT-DIGEST", true, true, 409, "error"),
                Arguments.of("client-digest", null, true, true, 409, "error"),
                Arguments.of(null, "service-digest", true, true, 409, "error"),
                Arguments.of(null, null, true, true, 409, "error"),
                Arguments.of("", "", true, true, 409, "error"),
                Arguments.of(" ", " ", true, true, 409, "error"),
                Arguments.of("123", 123, true, true, 409, "error"),
                Arguments.of("client-digest", "other-digest", false, true, 200, "pending"),
                Arguments.of("client-digest", null, true, false, 200, "pending"));
    }

    private HttpResponse<String> get(String path, String userAgent) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (userAgent != null) {
            request.header("User-Agent", userAgent);
        }
        return rest.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
