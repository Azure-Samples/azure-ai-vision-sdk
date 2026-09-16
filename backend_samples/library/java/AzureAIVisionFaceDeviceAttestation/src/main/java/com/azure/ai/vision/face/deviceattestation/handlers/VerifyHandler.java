package com.azure.ai.vision.face.deviceattestation.handlers;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.IsoTime;
import com.azure.ai.vision.face.deviceattestation.Json;
import com.azure.ai.vision.face.deviceattestation.JsonData;
import com.azure.ai.vision.face.deviceattestation.JsonParseException;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.crypto.CertUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.CertificateExpirationInfo;
import com.azure.ai.vision.face.deviceattestation.crypto.CryptoUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.EcKeyPair;
import com.azure.ai.vision.face.deviceattestation.models.AttestationVerifyBody;
import com.azure.ai.vision.face.deviceattestation.models.AttestationVerifyRequest;
import com.azure.ai.vision.face.deviceattestation.models.AttestationVerifyResult;
import com.azure.ai.vision.face.deviceattestation.services.ApiTelemetry;
import com.azure.ai.vision.face.deviceattestation.services.CertStore;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheck;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheckArgs;
import com.azure.ai.vision.face.deviceattestation.services.IosAssertionCheckResult;
import com.azure.ai.vision.face.deviceattestation.services.ServerUtils;
import com.azure.ai.vision.face.deviceattestation.services.SessionData;
import com.azure.ai.vision.face.deviceattestation.store.CertificateData;

/**
 * POST /api/attestation/verify — for an already-registered client, re-checks the
 * auth-cert signature (and, on iOS, a fresh App Attest assertion), then issues
 * the server encryption key. Returns {@code { exists: false }} when the cert has
 * not been registered yet.
 */
public final class VerifyHandler extends HandlerBase {

    public static final String ROUTE = "attestation/verify";

    private static final String PEM_MARKER = "-----BEGIN CERTIFICATE-----";

    public VerifyHandler(AttestationContext ctx) {
        super(ctx);
    }

    public HandlerOutcome handle(AttestationVerifyRequest req) {
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

        AttestationVerifyBody body = req.body;
        if (body == null) {
            return fail(ROUTE, 400, "INVALID_JSON_BODY", "Invalid JSON body", props(sessionId));
        }

        String payload = body.payload;
        String authPublicCert = body.authPublicCert;
        String signature = body.signature;
        String assertion = body.assertion;

        if (isEmpty(payload) || isEmpty(authPublicCert) || isEmpty(signature)) {
            return fail(ROUTE, 400, "MISSING_BODY_FIELDS", "Missing required fields: payload, authPublicCert, signature", props(sessionId));
        }
        if (payload.trim().isEmpty()) {
            return fail(ROUTE, 400, "INVALID_PAYLOAD_FORMAT", "Invalid payload format. Expected JSON string", props(sessionId));
        }

        String challengeHash;
        String encryptionPublicCert;
        try {
            Map<String, Object> parsed = Json.parseObject(payload);
            challengeHash = parsed == null ? null : JsonData.getString(parsed, "challengeHash");
            encryptionPublicCert = parsed == null ? null : JsonData.getString(parsed, "encryptionPublicCert");
            if (isEmpty(challengeHash) || isEmpty(encryptionPublicCert)) {
                return fail(ROUTE, 400, "MISSING_PAYLOAD_FIELDS", "Payload must contain challengeHash and encryptionPublicCert", props(sessionId));
            }
        } catch (JsonParseException e) {
            return fail(ROUTE, 400, "PAYLOAD_JSON_PARSE_ERROR", "Invalid payload JSON format", props(sessionId));
        }

        if (!authPublicCert.contains(PEM_MARKER) || !encryptionPublicCert.contains(PEM_MARKER)) {
            return fail(ROUTE, 400, "INVALID_CERT_PEM", "Invalid certificate format. Expected PEM format", props(sessionId));
        }

        int maxCertSize = config().maxCertSize();
        if (authPublicCert.length() > maxCertSize || encryptionPublicCert.length() > maxCertSize) {
            return fail(ROUTE, 400, "CERT_TOO_LARGE", "Certificate size exceeds maximum allowed size of " + maxCertSize + " bytes",
                    Maps.of("sid", sessionId, "maxCertSize", maxCertSize,
                            "authBytes", authPublicCert.length(), "encryptionBytes", encryptionPublicCert.length()));
        }

        try {
            SessionData sessionData = ServerUtils.getSessionData(ctx, sessionId);
            if (sessionData == null) {
                return fail(ROUTE, 404, "SESSION_NOT_FOUND", "Session not found", props(sessionId));
            }

            Map<String, Object> data = sessionData.data();
            String storedChallengeHash = JsonData.getString(data, "challengeHash");
            if (isEmpty(storedChallengeHash)) {
                return fail(ROUTE, 400, "CHALLENGE_NOT_INITIALIZED",
                        "Challenge hash not found in session. Session may have expired or not been initialized.", props(sessionId));
            }
            if (!challengeHash.equals(storedChallengeHash)) {
                return fail(ROUTE, 401, "CHALLENGE_HASH_MISMATCH", "Invalid challenge hash", props(sessionId));
            }

            String storedClientId = JsonData.getString(data, "clientId");
            if (storedClientId != null && !storedClientId.isEmpty() && !storedClientId.equals(clientId.trim())) {
                return fail(ROUTE, 401, "CLIENT_ID_MISMATCH", "Client ID mismatch",
                        Maps.of("sid", sessionId, "expectedClientId", storedClientId, "actualClientId", clientId.trim()));
            }
            String storedSystem = JsonData.getString(data, "system");
            if (storedSystem != null && !storedSystem.isEmpty() && !storedSystem.equals(systemLower)) {
                return fail(ROUTE, 401, "SYSTEM_MISMATCH", "System mismatch",
                        Maps.of("sid", sessionId, "expectedSystem", storedSystem, "actualSystem", systemLower));
            }

            CertificateExpirationInfo authExpiration = CertUtils.validateCertificateExpiration(authPublicCert);
            if (authExpiration == null) {
                return fail(ROUTE, 500, "AUTH_CERT_EXP_VALIDATION_FAIL", "Failed to validate authentication certificate expiration", props(sessionId));
            }
            if (authExpiration.isExpired()) {
                String expiredAt = IsoTime.from(authExpiration.notAfter());
                return fail(ROUTE, 403, "AUTH_CERT_EXPIRED", "Authentication certificate has expired",
                        Maps.of("sid", sessionId, "expiredAt", expiredAt), expiredAt, null);
            }
            if (authExpiration.isNotYetValid()) {
                String validFrom = IsoTime.from(authExpiration.notBefore());
                return fail(ROUTE, 403, "AUTH_CERT_NOT_YET_VALID", "Authentication certificate is not yet valid",
                        Maps.of("sid", sessionId, "validFrom", validFrom), null, validFrom);
            }

            String authPublicKey = CertUtils.extractPublicKeyFromCert(authPublicCert);
            if (authPublicKey == null) {
                return fail(ROUTE, 500, "AUTH_PUBKEY_EXTRACT_FAIL", "Failed to extract public key from authentication certificate", props(sessionId));
            }

            if (!CryptoUtils.verifySignatureEC(payload, signature, authPublicKey)) {
                return fail(ROUTE, 401, "PAYLOAD_SIGNATURE_INVALID", "Invalid signature", props(sessionId));
            }

            String thumbprint = CertUtils.computeCertThumbprint(authPublicCert);
            if (thumbprint == null) {
                return fail(ROUTE, 500, "THUMBPRINT_COMPUTE_FAIL", "Failed to compute certificate thumbprint", props(sessionId));
            }

            var snapshot = CertStore.getCertificate(ctx, thumbprint);
            CertificateData certRecord = snapshot == null ? null : snapshot.value();
            if (certRecord == null) {
                ApiTelemetry.trackApiFail(logger(), ROUTE, "CERT_NOT_REGISTERED", 200,
                        Maps.of("sid", sessionId, "thumbprint", thumbprint, "clientId", clientId.trim(), "system", systemLower));
                return ok(new AttestationVerifyResult(false, null));
            }

            if (!certRecord.clientId.equals(clientId.trim())) {
                return fail(ROUTE, 401, "CERT_CLIENT_ID_MISMATCH", "Certificate clientId mismatch",
                        Maps.of("sid", sessionId, "thumbprint", thumbprint, "expectedClientId", certRecord.clientId, "actualClientId", clientId.trim()));
            }
            if (!certRecord.system.equals(systemLower)) {
                return fail(ROUTE, 401, "CERT_SYSTEM_MISMATCH", "Certificate system mismatch",
                        Maps.of("sid", sessionId, "thumbprint", thumbprint, "expectedSystem", certRecord.system, "actualSystem", systemLower));
            }

            java.util.function.BooleanSupplier commitAssertion = null;
            if (systemLower.equals("ios")) {
                IosAssertionCheckResult check = IosAssertionCheck.check(ctx, new IosAssertionCheckArgs(
                        ROUTE, sessionId, thumbprint, payload.getBytes(StandardCharsets.UTF_8), assertion));
                if (!check.ok) {
                    return check.result;
                }
                commitAssertion = check.commit;
            }

            if (JsonData.getBool(data, "serverKeyGenerated")) {
                return fail(ROUTE, 409, "SERVER_KEYS_ALREADY_GENERATED", "Server keys already generated for this session", props(sessionId));
            }

            CertificateExpirationInfo encExpiration = CertUtils.validateCertificateExpiration(encryptionPublicCert);
            if (encExpiration == null) {
                return fail(ROUTE, 500, "ENC_CERT_EXP_VALIDATION_FAIL", "Failed to validate encryption certificate expiration", props(sessionId));
            }
            if (encExpiration.isExpired()) {
                String expiredAt = IsoTime.from(encExpiration.notAfter());
                return fail(ROUTE, 403, "ENC_CERT_EXPIRED", "Encryption certificate has expired",
                        Maps.of("sid", sessionId, "expiredAt", expiredAt), expiredAt, null);
            }
            if (encExpiration.isNotYetValid()) {
                String validFrom = IsoTime.from(encExpiration.notBefore());
                return fail(ROUTE, 403, "ENC_CERT_NOT_YET_VALID", "Encryption certificate is not yet valid",
                        Maps.of("sid", sessionId, "validFrom", validFrom), null, validFrom);
            }

            String clientEncryptionPublicKey = CertUtils.extractPublicKeyFromCert(encryptionPublicCert);
            if (clientEncryptionPublicKey == null) {
                return fail(ROUTE, 500, "ENC_PUBKEY_EXTRACT_FAIL", "Failed to extract public key from encryption certificate", props(sessionId));
            }

            EcKeyPair keyPair = CryptoUtils.generateServerKeyPairEC();
            if (keyPair == null) {
                return fail(ROUTE, 500, "SERVER_KEYPAIR_GEN_FAIL", "Failed to generate server key pair", props(sessionId));
            }

            data.put("serverKeyGenerated", true);
            data.put("serverEncryptionPrivateKey", keyPair.privateKey());
            data.put("serverEncryptionPublicKey", keyPair.publicKey());
            data.put("clientAuthPublicKey", authPublicKey);
            data.put("clientEncryptionPublicKey", clientEncryptionPublicKey);
            data.put("certRegistered", true);
            data.put("clientAuthCertThumbprint", thumbprint);

            if (commitAssertion != null && !commitAssertion.getAsBoolean()) {
                return fail(ROUTE, 409, "ASSERTION_STATE_CONFLICT", "Certificate changed or expired; obtain a fresh assertion");
            }
            boolean updated = ServerUtils.updateSessionData(ctx, sessionId, sessionData.token(), data, sessionData.version());
            if (!updated) {
                return fail(ROUTE, 500, "UPDATE_SESSION_FAIL", "Failed to update session with server keys", props(sessionId));
            }

            return ok(new AttestationVerifyResult(true, keyPair.publicKey()));
        } catch (com.azure.ai.vision.face.deviceattestation.store.StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return fail(ROUTE, 500, "STORAGE_ERROR", "Storage operation failed",
                    Maps.of("sid", sessionId, "errorMessage", ex.getMessage()));
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
