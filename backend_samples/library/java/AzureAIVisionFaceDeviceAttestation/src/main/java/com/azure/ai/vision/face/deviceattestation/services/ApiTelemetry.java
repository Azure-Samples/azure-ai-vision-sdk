package com.azure.ai.vision.face.deviceattestation.services;

import java.util.HashMap;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;

/**
 * Per-route failure telemetry. Emits a structured "Api.Fail" event so failure
 * reasons can be grouped/queried by route + reason code.
 */
public final class ApiTelemetry {

    public static void trackApiFail(
            AttestationLogger logger,
            String route,
            String reason,
            int status,
            Map<String, Object> properties) {
        Map<String, Object> props = new HashMap<>();
        props.put("route", route);
        props.put("reason", reason);
        props.put("status", status);
        if (properties != null) {
            props.putAll(properties);
        }
        logger.trackEvent("Api.Fail", props, null);
    }

    private ApiTelemetry() {
    }
}
