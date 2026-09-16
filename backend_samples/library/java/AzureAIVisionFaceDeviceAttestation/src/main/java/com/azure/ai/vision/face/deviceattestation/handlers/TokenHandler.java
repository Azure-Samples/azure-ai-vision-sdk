package com.azure.ai.vision.face.deviceattestation.handlers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.IsoTime;
import com.azure.ai.vision.face.deviceattestation.Json;
import com.azure.ai.vision.face.deviceattestation.JsonData;
import com.azure.ai.vision.face.deviceattestation.JsonParseException;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.crypto.CryptoUtils;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenBody;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenRequest;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenSuccess;
import com.azure.ai.vision.face.deviceattestation.services.ApiTelemetry;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheck;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheckArgs;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheckResult;
import com.azure.ai.vision.face.deviceattestation.services.ServerUtils;
import com.azure.ai.vision.face.deviceattestation.services.SessionData;

/**
 * POST /api/session/token — completes authentication: verifies the auth-cert
 * signature (and, on iOS, a fresh assertion) over the encrypted request,
 * decrypts it, checks the enclosed challenge/clientId/system against the
 * session, marks it authenticated, and returns the Face token encrypted to the
 * client.
 */
public final class TokenHandler extends HandlerBase {

    public static final String ROUTE = "session/token";

    public TokenHandler(AttestationContext ctx) {
        super(ctx);
    }

    public HandlerOutcome handle(SessionTokenRequest req) {
        String sessionId = req.sessionId;
        if (sessionId == null || sessionId.isEmpty()) {
            return fail(ROUTE, 400, "MISSING_SESSION_ID", "Missing session ID");
        }
        if (!isUuid(sessionId)) {
            return fail(ROUTE, 400, "INVALID_SESSION_ID", "Invalid session ID format");
        }

        SessionTokenBody body = req.body;
        if (body == null) {
            return fail(ROUTE, 400, "INVALID_JSON_BODY", "Invalid JSON body", props(sessionId));
        }
        if (isEmpty(body.encryptedData)) {
            return fail(ROUTE, 400, "MISSING_ENCRYPTED_DATA", "Missing or invalid \"encryptedData\" field (expected base64 string)", props(sessionId));
        }
        if (isEmpty(body.signature)) {
            return fail(ROUTE, 400, "MISSING_SIGNATURE", "Missing or invalid \"signature\" field", props(sessionId));
        }

        SessionData sessionData = ServerUtils.getSessionData(ctx, sessionId);
        if (sessionData == null) {
            return fail(ROUTE, 404, "SESSION_NOT_FOUND", "Session not found", props(sessionId));
        }

        Map<String, Object> data = sessionData.data();
        if (!JsonData.getBool(data, "serverKeyGenerated")) {
            return fail(ROUTE, 409, "SERVER_KEYS_NOT_GENERATED", "Server keys not generated. Call /api/attestation/verify or /api/attestation/register first", props(sessionId));
        }
        if (!JsonData.getBool(data, "certRegistered")) {
            return fail(ROUTE, 409, "CERT_NOT_REGISTERED", "Certificate not registered. Call /api/attestation/verify or /api/attestation/register first", props(sessionId));
        }
        if (JsonData.getBool(data, "authCompleted")) {
            return fail(ROUTE, 409, "AUTH_ALREADY_COMPLETED", "Authentication already completed for this session", props(sessionId));
        }

        String clientAuthPublicKey = JsonData.getString(data, "clientAuthPublicKey");
        if (isEmpty(clientAuthPublicKey)) {
            return fail(ROUTE, 500, "MISSING_CLIENT_AUTH_PUBKEY", "Client authentication public key not found in session", props(sessionId));
        }
        String clientEncryptionPublicKey = JsonData.getString(data, "clientEncryptionPublicKey");
        if (isEmpty(clientEncryptionPublicKey)) {
            return fail(ROUTE, 500, "MISSING_CLIENT_ENC_PUBKEY", "Client encryption public key not found in session", props(sessionId));
        }

        if (!CryptoUtils.verifySignatureEC(body.encryptedData, body.signature, clientAuthPublicKey)) {
            return fail(ROUTE, 401, "SIGNATURE_INVALID", "Signature verification failed", props(sessionId));
        }

        java.util.function.BooleanSupplier commitAssertion = null;
        if ("ios".equals(JsonData.getString(data, "system"))) {
            String thumbprint = JsonData.getString(data, "clientAuthCertThumbprint");
            if (isEmpty(thumbprint)) {
                return fail(ROUTE, 500, "MISSING_CERT_THUMBPRINT", "Client cert thumbprint not found in session", props(sessionId));
            }
            IosAssertionCheckResult check = IosAssertionCheck.check(ctx, new IosAssertionCheckArgs(
                    ROUTE, sessionId, thumbprint, body.encryptedData.getBytes(StandardCharsets.UTF_8), body.assertion));
            if (!check.ok) {
                return check.result;
            }
            commitAssertion = check.commit;
        }

        String serverEncryptionPrivateKey = JsonData.getString(data, "serverEncryptionPrivateKey");
        if (isEmpty(serverEncryptionPrivateKey)) {
            return fail(ROUTE, 500, "MISSING_SERVER_ENC_PRIVKEY", "Server encryption private key not found in session", props(sessionId));
        }

        String decryptedPayload = CryptoUtils.decryptWithPrivateKeyEC(body.encryptedData, serverEncryptionPrivateKey);
        if (decryptedPayload == null) {
            return fail(ROUTE, 401, "DECRYPTION_FAIL", "Decryption failed", props(sessionId));
        }

        String msgChallenge;
        String msgClientId;
        String msgSystem;
        try {
            Map<String, Object> parsed = Json.parseObject(decryptedPayload);
            msgChallenge = parsed == null ? null : JsonData.getString(parsed, "challengeHash");
            msgClientId = parsed == null ? null : JsonData.getString(parsed, "clientId");
            msgSystem = parsed == null ? null : JsonData.getString(parsed, "system");
        } catch (JsonParseException e) {
            return fail(ROUTE, 401, "DECRYPTED_PAYLOAD_PARSE_ERROR", "Invalid decrypted payload format", props(sessionId));
        }

        if (isEmpty(msgChallenge) || isEmpty(msgClientId) || isEmpty(msgSystem)
                || !msgChallenge.equals(JsonData.getString(data, "challengeHash"))
                || !msgClientId.equals(JsonData.getString(data, "clientId"))
                || !msgSystem.equals(JsonData.getString(data, "system"))) {
            return fail(ROUTE, 401, "PAYLOAD_MISMATCH", "Payload mismatch", Maps.of(
                    "sid", sessionId,
                    "hasChallenge", !isEmpty(msgChallenge),
                    "hasClientId", !isEmpty(msgClientId),
                    "hasSystem", !isEmpty(msgSystem)));
        }

        data.put("authCompleted", true);
        String responsePayload = Json.stringify(Maps.of("token", sessionData.token(), "timestamp", IsoTime.now()));
        String encryptedResponse = CryptoUtils.encryptWithPublicKeyEC(responsePayload, clientEncryptionPublicKey);
        if (encryptedResponse == null) {
            return fail(ROUTE, 500, "RESPONSE_ENCRYPT_FAIL", "Failed to encrypt response", props(sessionId));
        }

        if (commitAssertion != null && !commitAssertion.getAsBoolean()) {
            return fail(ROUTE, 409, "ASSERTION_STATE_CONFLICT", "Certificate changed or expired; obtain a fresh assertion");
        }
        if (!ServerUtils.updateSessionData(ctx, sessionId, sessionData.token(), data, sessionData.version())) {
            return fail(ROUTE, 409, "SESSION_STATE_CONFLICT", "Session changed or expired");
        }
        return ok(new SessionTokenSuccess(encryptedResponse));
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
