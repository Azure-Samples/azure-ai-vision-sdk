package com.azure.ai.vision.face.deviceattestation.handlers;

import java.util.LinkedHashMap;
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
import com.azure.ai.vision.face.deviceattestation.crypto.CertificateValidationResult;
import com.azure.ai.vision.face.deviceattestation.crypto.CryptoUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.EcKeyPair;
import com.azure.ai.vision.face.deviceattestation.models.AndroidKeyAttestationInfo;
import com.azure.ai.vision.face.deviceattestation.models.AttestationRegisterBody;
import com.azure.ai.vision.face.deviceattestation.models.AttestationRegisterData;
import com.azure.ai.vision.face.deviceattestation.models.AttestationRegisterRequest;
import com.azure.ai.vision.face.deviceattestation.models.AttestationRegisterSuccess;
import com.azure.ai.vision.face.deviceattestation.services.ApiTelemetry;
import com.azure.ai.vision.face.deviceattestation.services.AttestationMessageData;
import com.azure.ai.vision.face.deviceattestation.services.AuthVerification;
import com.azure.ai.vision.face.deviceattestation.services.AuthVerificationResult;
import com.azure.ai.vision.face.deviceattestation.services.CertStore;
import com.azure.ai.vision.face.deviceattestation.services.SaveCertificateResult;
import com.azure.ai.vision.face.deviceattestation.services.ServerUtils;
import com.azure.ai.vision.face.deviceattestation.services.SessionData;

/**
 * POST /api/attestation/register — verifies the platform attestation (Play
 * Integrity / App Attest), persists the client's auth certificate, generates
 * the server EC key pair, and stores the exchanged public keys on the session.
 */
public final class RegisterHandler extends HandlerBase {

    public static final String ROUTE = "attestation/register";

    private static final String PEM_MARKER = "-----BEGIN CERTIFICATE-----";

    public RegisterHandler(AttestationContext ctx) {
        super(ctx);
    }

    public HandlerOutcome handle(AttestationRegisterRequest req) {
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

        AttestationRegisterBody body = req.body;
        if (body == null) {
            return fail(ROUTE, 400, "INVALID_JSON_BODY", "Invalid JSON body", props(sessionId));
        }

        String payload = body.payload;
        String authPublicCert = body.authPublicCert;
        String signature = body.signature;

        if (isEmpty(payload) || isEmpty(authPublicCert) || isEmpty(signature)) {
            return fail(ROUTE, 400, "MISSING_BODY_FIELDS", "Missing required fields: payload, authPublicCert, signature", props(sessionId));
        }
        if (payload.trim().isEmpty()) {
            return fail(ROUTE, 400, "INVALID_PAYLOAD_FORMAT", "Invalid payload format. Expected JSON string", props(sessionId));
        }

        String challengeHash;
        String encryptionPublicCert;
        String attestJson;
        try {
            Map<String, Object> parsed = Json.parseObject(payload);
            challengeHash = parsed == null ? null : JsonData.getString(parsed, "challengeHash");
            encryptionPublicCert = parsed == null ? null : JsonData.getString(parsed, "encryptionPublicCert");
            attestJson = parsed == null ? null : JsonData.getString(parsed, "attestJson");
            if (isEmpty(challengeHash) || isEmpty(encryptionPublicCert) || isEmpty(attestJson)) {
                return fail(ROUTE, 400, "MISSING_PAYLOAD_FIELDS", "Payload must contain challengeHash, encryptionPublicCert, and attestJson", props(sessionId));
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
            String storedClientId = JsonData.getString(data, "clientId");
            String storedSystem = JsonData.getString(data, "system");
            if (isEmpty(storedChallengeHash) || isEmpty(storedClientId) || isEmpty(storedSystem)) {
                return fail(ROUTE, 409, "SESSION_NOT_INITIALIZED", "Session not initialized. Call /api/attestation/challenge first", props(sessionId));
            }

            if (!challengeHash.equals(storedChallengeHash)) {
                return fail(ROUTE, 401, "CHALLENGE_HASH_MISMATCH", "Invalid challenge hash", props(sessionId));
            }

            if (!clientId.trim().equals(storedClientId) || !systemLower.equals(storedSystem)) {
                return fail(ROUTE, 401, "CLIENT_OR_SYSTEM_MISMATCH", "Client ID or system mismatch",
                        Maps.of("sid", sessionId, "requestClientId", clientId.trim(), "sessionClientId", storedClientId,
                                "requestSystem", systemLower, "sessionSystem", storedSystem));
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

            CertificateValidationResult certValidation = CertUtils.validateCertificate(authPublicCert);
            if (certValidation == null || !certValidation.valid()) {
                return fail(ROUTE, 400, "AUTH_CERT_STRUCTURE_INVALID", "Invalid authentication certificate", props(sessionId));
            }

            String authPublicKey = CertUtils.extractPublicKeyFromCert(authPublicCert);
            if (authPublicKey == null) {
                return fail(ROUTE, 500, "AUTH_PUBKEY_EXTRACT_FAIL", "Failed to extract public key from authentication certificate", props(sessionId));
            }

            if (!CryptoUtils.verifySignatureEC(payload, signature, authPublicKey)) {
                return fail(ROUTE, 401, "PAYLOAD_SIGNATURE_INVALID", "Invalid signature", props(sessionId));
            }

            AttestationMessageData messageData = new AttestationMessageData(storedChallengeHash, storedClientId, storedSystem, authPublicCert);

            AuthVerificationResult verificationResult = AuthVerification.verifyAuthBySystem(ctx, messageData, attestJson);
            if (!verificationResult.verified) {
                return fail(ROUTE, 401, "ATTESTATION_VERIFICATION_FAIL", "Attestation verification failed",
                        Maps.of("sid", sessionId, "platform", verificationResult.platform, "verificationMessage", verificationResult.message));
            }

            String thumbprint = CertUtils.computeCertThumbprint(authPublicCert);
            if (thumbprint == null) {
                return fail(ROUTE, 500, "THUMBPRINT_COMPUTE_FAIL", "Failed to compute certificate thumbprint", props(sessionId));
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("verificationTimestamp", verificationResult.timestamp);
            metadata.put("attestJson", attestJson);
            if (verificationResult.integrityVerdict != null) {
                metadata.put("integrityVerdict", verificationResult.integrityVerdict);
            }
            if (verificationResult.appAttestVerdict != null) {
                metadata.put("appAttestVerdict", verificationResult.appAttestVerdict);
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

            SaveCertificateResult saveResult = CertStore.saveCertificate(ctx, clientId.trim(), systemLower, authPublicCert, metadata);
            if (saveResult == null) {
                return fail(ROUTE, 409, "SAVE_CERT_FAIL", "Certificate changed or could not be created");
            }

            data.put("serverKeyGenerated", true);
            data.put("serverEncryptionPrivateKey", keyPair.privateKey());
            data.put("serverEncryptionPublicKey", keyPair.publicKey());
            data.put("clientAuthPublicKey", authPublicKey);
            data.put("clientEncryptionPublicKey", clientEncryptionPublicKey);
            data.put("certRegistered", true);
            data.put("clientAuthCertThumbprint", saveResult.thumbprint());

            boolean updated = ServerUtils.updateSessionData(ctx, sessionId, sessionData.token(), data, sessionData.version());
            if (!updated) {
                return fail(ROUTE, 500, "UPDATE_SESSION_FAIL", "Failed to update session with server keys", props(sessionId));
            }

            AttestationRegisterData registerData = new AttestationRegisterData();
            registerData.platform = verificationResult.platform;
            registerData.warnings = verificationResult.warnings;
            if (systemLower.equals("ios")) {
                registerData.appAttestVerdict = verificationResult.appAttestVerdict;
            } else {
                AndroidKeyAttestationInfo info = new AndroidKeyAttestationInfo();
                info.chainLength = verificationResult.chainLength;
                info.rootCA = verificationResult.rootCA;
                info.leafCertValidityWarning = verificationResult.leafCertValidityWarning;
                registerData.androidKeyAttestation = info;
                registerData.integrityVerdict = verificationResult.integrityVerdict;
            }

            return ok(new AttestationRegisterSuccess("Certificate stored successfully", keyPair.publicKey()), 200, null, registerData);
        } catch (com.azure.ai.vision.face.deviceattestation.store.StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            ApiTelemetry.trackApiFail(logger(), ROUTE, "INTERNAL_ERROR", 500,
                    Maps.of("sid", sessionId, "errorMessage", ex.getMessage()));
            throw ex;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
