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
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestBody;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestData;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestRequest;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestSuccess;
import com.azure.ai.vision.face.deviceattestation.services.ApiTelemetry;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheck;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheckArgs;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheckResult;
import com.azure.ai.vision.face.deviceattestation.services.ServerUtils;
import com.azure.ai.vision.face.deviceattestation.services.SessionData;

/**
 * POST /api/liveness/digest — accepts the client's signed + encrypted liveness
 * digest: verifies the auth-cert signature (and, on iOS, a fresh assertion),
 * decrypts the payload, checks clientId/os against the session, records the
 * digest, and returns an encrypted acknowledgement.
 */
public final class DigestHandler extends HandlerBase {

    public static final String ROUTE = "liveness/digest";

    public DigestHandler(AttestationContext ctx) {
        super(ctx);
    }

    public HandlerOutcome handle(LivenessDigestRequest req) {
        String sessionId = req.sessionId;
        if (sessionId == null || sessionId.isEmpty()) {
            return fail(ROUTE, 400, "MISSING_SESSION_ID", "Missing session ID");
        }
        if (!isUuid(sessionId)) {
            return fail(ROUTE, 400, "INVALID_SESSION_ID", "Invalid session ID format");
        }

        LivenessDigestBody body = req.body;
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
        if (!JsonData.getBool(data, "authCompleted")) {
            return fail(ROUTE, 409, "AUTH_NOT_COMPLETED", "Obtain the session token before submitting a digest");
        }
        if (JsonData.getBool(data, "digestCompleted")) {
            return fail(ROUTE, 409, "DIGEST_ALREADY_EXISTS", "Digest already exists for this session", props(sessionId));
        }

        String clientAuthPublicKey = JsonData.getString(data, "clientAuthPublicKey");
        if (isEmpty(clientAuthPublicKey)) {
            return fail(ROUTE, 500, "MISSING_CLIENT_AUTH_PUBKEY", "Client auth public key not found in session", props(sessionId));
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

        String cid;
        String os;
        String digestValue;
        try {
            Map<String, Object> parsed = Json.parseObject(decryptedPayload);
            cid = parsed == null ? null : JsonData.getString(parsed, "cid");
            os = parsed == null ? null : JsonData.getString(parsed, "os");
            digestValue = parsed == null ? null : JsonData.getString(parsed, "digest");
        } catch (JsonParseException e) {
            return fail(ROUTE, 401, "DECRYPTED_PAYLOAD_PARSE_ERROR", "Invalid decrypted payload format", props(sessionId));
        }

        if (isEmpty(cid) || cid.trim().isEmpty()) {
            return fail(ROUTE, 400, "MISSING_CID", "Missing or invalid \"cid\" in payload", props(sessionId));
        }

        String clientId = cid.trim();
        os = os == null ? "" : os;
        if (digestValue == null || digestValue.isBlank()) {
            return fail(ROUTE, 400, "INVALID_DIGEST", "Expected a nonempty digest");
        }

        String storedClientId = JsonData.getString(data, "clientId");
        if (storedClientId != null && !storedClientId.isEmpty() && !storedClientId.equals(clientId)) {
            return fail(ROUTE, 401, "CLIENT_ID_MISMATCH", "Client ID mismatch",
                    Maps.of("sid", sessionId, "expectedClientId", storedClientId, "actualClientId", clientId));
        }
        String storedSystem = JsonData.getString(data, "system");
        if (storedSystem != null && !storedSystem.isEmpty() && !storedSystem.equals(os)) {
            return fail(ROUTE, 401, "OS_MISMATCH", "Operating system mismatch",
                    Maps.of("sid", sessionId, "expectedSystem", storedSystem, "actualOs", os));
        }

        data.put("digest", digestValue);
        data.put("attestationClientId", clientId);
        data.put("attestationOs", os);
        data.put("digestCompleted", true);

        String responsePayload = Json.stringify(Maps.of("success", true, "timestamp", IsoTime.now()));

        String clientEncryptionPublicKey = JsonData.getString(data, "clientEncryptionPublicKey");
        if (isEmpty(clientEncryptionPublicKey)) {
            return fail(ROUTE, 500, "MISSING_CLIENT_ENC_PUBKEY", "Client encryption public key not found in session", props(sessionId));
        }

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
        return ok(new LivenessDigestSuccess(encryptedResponse), 200, null, new LivenessDigestData(digestValue));
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
