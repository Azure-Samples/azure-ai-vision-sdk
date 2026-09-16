package com.azure.ai.vision.face.sample.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.azure.ai.vision.face.sample.config.AppSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin client for the Azure Face liveness-session REST endpoints. SSRF-guarded:
 * the resource name is validated and the domain is hardcoded.
 */
@Component
public final class FaceLivenessApi {

    /** Non-2xx response from the Face service. */
    public static final class FaceApiException extends RuntimeException {
        public final int status;
        public final String body;

        public FaceApiException(String message, int status, String body) {
            super(message);
            this.status = status;
            this.body = body;
        }
    }

    /** Result of creating a liveness session. */
    public record CreateSessionResult(String sessionId, String authToken) {
    }

    private static final Pattern RESOURCE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{1,62}$");

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiVersion;

    public FaceLivenessApi(AppSettings settings, RestClient.Builder http) {
        this.apiVersion = settings.getFaceApiVersion();
        this.http = http.build();
    }

    /** Start a liveness session (with or without a verify image). */
    public CreateSessionResult createSession(String resource, String apiKey, String mode, byte[] verifyImage, String verifyImageName) {
        boolean withVerify = verifyImage != null && verifyImage.length > 0;
        String action = withVerify ? "detectLivenessWithVerify" : "detectLiveness";
        String url = baseUrl(resource) + "/" + action + "-sessions";

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("livenessOperationMode", mode);
        parameters.put("enableSessionImage", true);
        parameters.put("deviceCorrelationIdSetInClient", true);
        parameters.put("deviceCorrelationIdSetInSessionStart", true);

        try {
            String responseJson;
            if (withVerify) {
                MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("livenessOperationMode", mode);
            form.add("enableSessionImage", "true");
            form.add("deviceCorrelationIdSetInClient", "true");
            form.add("deviceCorrelationIdSetInSessionStart", "true");
            form.add("verifyImage", new NamedByteArrayResource(verifyImage, verifyImageName));
                responseJson = http.post().uri(url)
                        .header("Ocp-Apim-Subscription-Key", apiKey)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve()
                        .body(String.class);
            } else {
                responseJson = http.post().uri(url)
                        .header("Ocp-Apim-Subscription-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(writeJson(parameters))
                        .retrieve()
                        .body(String.class);
            }
            JsonNode node = mapper.readTree(responseJson);
            return new CreateSessionResult(text(node, "sessionId"), text(node, "authToken"));
        } catch (RestClientResponseException e) {
            throw new FaceApiException("Face createSession failed", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FaceApiException("Face createSession error: " + e.getMessage(), 0, "");
        }
    }

    /** Fetch the liveness session result JSON. */
    public JsonNode querySessionResult(String resource, String apiKey, String action, String sessionId) {
        String url = baseUrl(resource) + "/" + action + "-sessions/" + sessionId;
        try {
            String responseJson = http.get().uri(url)
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(responseJson);
        } catch (RestClientResponseException e) {
            throw new FaceApiException("Face querySessionResult failed", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FaceApiException("Face querySessionResult error: " + e.getMessage(), 0, "");
        }
    }

    private String baseUrl(String resource) {
        if (resource == null || !RESOURCE.matcher(resource).matches()) {
            throw new FaceApiException("Invalid Face resource name", 400, "");
        }
        return "https://" + resource + ".cognitiveservices.azure.com/face/" + apiVersion;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new FaceApiException("JSON error", 0, "");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() ? child.asText() : null;
    }

    /** ByteArrayResource that reports a filename (required for multipart file parts). */
    private static final class NamedByteArrayResource extends org.springframework.core.io.ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename != null ? filename : "verify.jpg";
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
