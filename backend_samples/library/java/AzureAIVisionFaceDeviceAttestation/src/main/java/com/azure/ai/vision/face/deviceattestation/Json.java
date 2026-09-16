package com.azure.ai.vision.face.deviceattestation;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

/**
 * Internal JSON parse/serialize for the wire payloads (the signed request
 * payload and the encrypted response). Integers decode as {@link Long} (not
 * double) so sign-count comparisons stay exact.
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .disableHtmlEscaping()
            .create();

    /**
     * Parse a JSON document. Returns the decoded object as a map, or null when
     * the document is valid JSON but not an object. Throws
     * {@link JsonParseException} on malformed JSON.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        try {
            Object decoded = GSON.fromJson(json, Object.class);
            return decoded instanceof Map ? (Map<String, Object>) decoded : null;
        } catch (RuntimeException e) {
            throw new JsonParseException("Invalid JSON", e);
        }
    }

    /** Serialize a value (map / list / POJO) to a JSON string. */
    public static String stringify(Object value) {
        return GSON.toJson(value);
    }

    private Json() {
    }
}
