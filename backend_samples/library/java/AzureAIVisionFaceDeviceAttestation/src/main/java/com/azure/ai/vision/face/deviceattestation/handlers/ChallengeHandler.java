package com.azure.ai.vision.face.deviceattestation.handlers;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.JsonData;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.crypto.HashUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.HexUtils;
import com.azure.ai.vision.face.deviceattestation.models.AttestationChallengeRequest;
import com.azure.ai.vision.face.deviceattestation.models.AttestationChallengeSuccess;
import com.azure.ai.vision.face.deviceattestation.services.ServerUtils;
import com.azure.ai.vision.face.deviceattestation.services.SessionData;

/**
 * POST /api/attestation/challenge — issues a one-time random challenge hash for
 * a session and binds the caller's clientId + system into the session state.
 */
public final class ChallengeHandler extends HandlerBase {

    public static final String ROUTE = "attestation/challenge";

    private static final SecureRandom RANDOM = new SecureRandom();

    public ChallengeHandler(AttestationContext ctx) {
        super(ctx);
    }

    public HandlerOutcome handle(AttestationChallengeRequest req) {
        String sessionId = req.sessionId;
        if (sessionId == null || sessionId.isEmpty()) {
            return fail(ROUTE, 400, "MISSING_SESSION_ID", "Missing session ID");
        }
        if (!isUuid(sessionId)) {
            return fail(ROUTE, 400, "INVALID_SESSION_ID", "Invalid session ID format");
        }

        String clientId = req.clientId;
        if (clientId == null || clientId.isEmpty()) {
            return fail(ROUTE, 400, "MISSING_CLIENT_ID", "Missing client ID", props(sessionId));
        }
        if (clientId.trim().isEmpty()) {
            return fail(ROUTE, 400, "INVALID_CLIENT_ID", "Invalid client ID", props(sessionId));
        }

        String system = req.system;
        if (system == null || system.isEmpty()) {
            return fail(ROUTE, 400, "MISSING_SYSTEM", "Missing system parameter", props(sessionId));
        }
        String systemLower = system.toLowerCase(Locale.ROOT);
        if (!systemLower.equals("ios") && !systemLower.equals("android")) {
            return fail(ROUTE, 400, "INVALID_SYSTEM", "Invalid system parameter. Must be \"ios\" or \"android\"",
                    Maps.of("sid", sessionId, "system", systemLower));
        }

        SessionData sessionData = ServerUtils.getSessionData(ctx, sessionId);
        if (sessionData == null) {
            return fail(ROUTE, 404, "SESSION_NOT_FOUND", "Session not found", props(sessionId));
        }

        Map<String, Object> data = sessionData.data();
        String existing = JsonData.getString(data, "challengeHash");
        if (existing != null && !existing.isEmpty()) {
            return fail(ROUTE, 409, "CHALLENGE_ALREADY_EXISTS", "Challenge hash already exists for this session", props(sessionId));
        }

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String challengeHash = HexUtils.toHex(HashUtils.sha256(random));

        data.put("challengeHash", challengeHash);
        data.put("clientId", clientId.trim());
        data.put("system", systemLower);

        boolean updated = ServerUtils.updateSessionData(ctx, sessionId, sessionData.token(), data, sessionData.version());
        if (!updated) {
            return fail(ROUTE, 500, "UPDATE_SESSION_FAIL", "Failed to store challenge", props(sessionId));
        }

        return ok(new AttestationChallengeSuccess(challengeHash, clientId.trim(), systemLower));
    }
}
