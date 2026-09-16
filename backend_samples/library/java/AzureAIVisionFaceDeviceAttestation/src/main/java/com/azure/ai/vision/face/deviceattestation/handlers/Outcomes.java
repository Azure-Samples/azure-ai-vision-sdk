package com.azure.ai.vision.face.deviceattestation.handlers;

import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.services.ApiTelemetry;

/**
 * Builds {@link HandlerOutcome} values. {@link #fail} emits the failure
 * telemetry in the same step so the response code and telemetry code can never
 * drift.
 */
public final class Outcomes {

    /** Build a success (or otherwise non-failure) outcome. */
    public static HandlerOutcome ok(Object body, int status, String code, Object data) {
        boolean ok = status < 400;
        String message = body instanceof ErrorBody e ? e.message : null;
        return new HandlerOutcome(ok, code != null ? code : (ok ? "OK" : "ERROR"), status, body, message, data);
    }

    /** Build a 200 success outcome. */
    public static HandlerOutcome ok(Object body) {
        return ok(body, 200, null, null);
    }

    /** Build a FAILURE outcome AND emit its failure telemetry in one step. */
    public static HandlerOutcome fail(
            AttestationLogger logger,
            String route,
            int status,
            String code,
            String message,
            Map<String, Object> properties,
            String expiredAt,
            String validFrom) {
        ApiTelemetry.trackApiFail(logger, route, code, status, properties);
        return new HandlerOutcome(false, code, status, new ErrorBody(message, expiredAt, validFrom), message, null);
    }

    private Outcomes() {
    }
}
