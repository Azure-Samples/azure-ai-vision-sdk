package com.azure.ai.vision.face.deviceattestation.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;

/**
 * Base for the attestation route handlers: exposes the injected context and the
 * shared {@link #fail}/{@link #ok} builders and UUID check.
 */
public abstract class HandlerBase {

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[\\da-f]{8}-([\\da-f]{4}-){3}[\\da-f]{12}$", Pattern.CASE_INSENSITIVE);

    protected final AttestationContext ctx;

    protected HandlerBase(AttestationContext ctx) {
        this.ctx = ctx;
    }

    protected AttestationConfig config() {
        return ctx.config;
    }

    protected ClusterStore store() {
        return ctx.store;
    }

    protected AttestationLogger logger() {
        return ctx.logger;
    }

    protected static boolean isUuid(String value) {
        return value != null && UUID_REGEX.matcher(value).matches();
    }

    protected HandlerOutcome fail(String route, int status, String code, String message) {
        return Outcomes.fail(logger(), route, status, code, message, null, null, null);
    }

    protected HandlerOutcome fail(String route, int status, String code, String message, Map<String, Object> properties) {
        return Outcomes.fail(logger(), route, status, code, message, properties, null, null);
    }

    protected HandlerOutcome fail(
            String route,
            int status,
            String code,
            String message,
            Map<String, Object> properties,
            String expiredAt,
            String validFrom) {
        return Outcomes.fail(logger(), route, status, code, message, properties, expiredAt, validFrom);
    }

    protected static HandlerOutcome ok(Object body) {
        return Outcomes.ok(body);
    }

    protected static HandlerOutcome ok(Object body, int status, String code, Object data) {
        return Outcomes.ok(body, status, code, data);
    }

    protected static Map<String, Object> props(String sid) {
        Map<String, Object> m = new HashMap<>();
        m.put("sid", sid);
        return m;
    }
}
