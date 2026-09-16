using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Azure.AI.Vision.Face.DeviceAttestation.Services;

namespace Azure.AI.Vision.Face.DeviceAttestation.Handlers;

/// <summary>
/// POST /api/attestation/verify — for an already-registered client, re-checks the
/// auth-cert signature (and, on iOS, a fresh App Attest assertion), then issues
/// the server encryption key. Returns <c>{ exists: false }</c> when the cert has
/// not been registered yet.
/// </summary>
internal sealed class VerifyHandler : HandlerBase
{
    public const string Route = "attestation/verify";

    public VerifyHandler(AttestationContext ctx) : base(ctx) { }

    public async Task<HandlerOutcome> HandleAsync(AttestationVerifyRequest req)
    {
        var sessionId = req.SessionId;
        if (string.IsNullOrEmpty(sessionId))
        {
            return Fail(Route, 400, "MISSING_SESSION_ID", "Missing session ID");
        }
        if (!ServerUtils.IsValidSessionId(sessionId))
        {
            return Fail(Route, 400, "INVALID_SESSION_ID", "Invalid session ID format");
        }

        var clientId = req.ClientId;
        if (string.IsNullOrEmpty(clientId))
        {
            return Fail(Route, 400, "MISSING_CLIENT_ID", "Missing client ID", Props(sessionId));
        }
        if (clientId.Trim().Length == 0)
        {
            return Fail(Route, 400, "INVALID_CLIENT_ID", "Invalid client ID", Props(sessionId));
        }

        var system = req.System;
        if (string.IsNullOrEmpty(system))
        {
            return Fail(Route, 400, "MISSING_SYSTEM", "Missing system parameter", Props(sessionId));
        }
        var systemLower = system.ToLowerInvariant();
        if (systemLower != "ios" && systemLower != "android")
        {
            return Fail(Route, 400, "INVALID_SYSTEM", "Invalid system parameter. Must be \"ios\" or \"android\"",
                new Dictionary<string, object?> { ["sid"] = sessionId, ["system"] = systemLower });
        }

        var body = req.Body;
        if (body is null)
        {
            return Fail(Route, 400, "INVALID_JSON_BODY", "Invalid JSON body", Props(sessionId));
        }

        var payload = body.Payload;
        var authPublicCert = body.AuthPublicCert;
        var signature = body.Signature;
        var assertion = body.Assertion;

        if (string.IsNullOrEmpty(payload) || string.IsNullOrEmpty(authPublicCert) || string.IsNullOrEmpty(signature))
        {
            return Fail(Route, 400, "MISSING_BODY_FIELDS", "Missing required fields: payload, authPublicCert, signature", Props(sessionId));
        }
        if (payload.Trim().Length == 0)
        {
            return Fail(Route, 400, "INVALID_PAYLOAD_FORMAT", "Invalid payload format. Expected JSON string", Props(sessionId));
        }

        string? challengeHash;
        string? encryptionPublicCert;
        try
        {
            var parsed = JsonNode.Parse(payload) as JsonObject;
            challengeHash = parsed is null ? null : JsonHelpers.GetString(parsed, "challengeHash");
            encryptionPublicCert = parsed is null ? null : JsonHelpers.GetString(parsed, "encryptionPublicCert");
            if (string.IsNullOrEmpty(challengeHash) || string.IsNullOrEmpty(encryptionPublicCert))
            {
                return Fail(Route, 400, "MISSING_PAYLOAD_FIELDS", "Payload must contain challengeHash and encryptionPublicCert", Props(sessionId));
            }
        }
        catch (JsonException)
        {
            return Fail(Route, 400, "PAYLOAD_JSON_PARSE_ERROR", "Invalid payload JSON format", Props(sessionId));
        }

        if (!authPublicCert.Contains("-----BEGIN CERTIFICATE-----") ||
            !encryptionPublicCert.Contains("-----BEGIN CERTIFICATE-----"))
        {
            return Fail(Route, 400, "INVALID_CERT_PEM", "Invalid certificate format. Expected PEM format", Props(sessionId));
        }

        var maxCertSize = Config.MaxCertSize;
        if (authPublicCert.Length > maxCertSize || encryptionPublicCert.Length > maxCertSize)
        {
            return Fail(Route, 400, "CERT_TOO_LARGE", $"Certificate size exceeds maximum allowed size of {maxCertSize} bytes",
                new Dictionary<string, object?>
                {
                    ["sid"] = sessionId,
                    ["maxCertSize"] = maxCertSize,
                    ["authBytes"] = authPublicCert.Length,
                    ["encryptionBytes"] = encryptionPublicCert.Length,
                });
        }

        try
        {
            var sessionData = await ServerUtils.GetSessionDataAsync(Ctx, sessionId);
            if (sessionData is null)
            {
                return Fail(Route, 404, "SESSION_NOT_FOUND", "Session not found", Props(sessionId));
            }

            var data = sessionData.Data;
            var storedChallengeHash = JsonHelpers.GetString(data, "challengeHash");
            if (string.IsNullOrEmpty(storedChallengeHash))
            {
                return Fail(Route, 400, "CHALLENGE_NOT_INITIALIZED",
                    "Challenge hash not found in session. Session may have expired or not been initialized.", Props(sessionId));
            }
            if (challengeHash != storedChallengeHash)
            {
                return Fail(Route, 401, "CHALLENGE_HASH_MISMATCH", "Invalid challenge hash", Props(sessionId));
            }

            var storedClientId = JsonHelpers.GetString(data, "clientId");
            if (!string.IsNullOrEmpty(storedClientId) && storedClientId != clientId.Trim())
            {
                return Fail(Route, 401, "CLIENT_ID_MISMATCH", "Client ID mismatch",
                    new Dictionary<string, object?> { ["sid"] = sessionId, ["expectedClientId"] = storedClientId, ["actualClientId"] = clientId.Trim() });
            }
            var storedSystem = JsonHelpers.GetString(data, "system");
            if (!string.IsNullOrEmpty(storedSystem) && storedSystem != systemLower)
            {
                return Fail(Route, 401, "SYSTEM_MISMATCH", "System mismatch",
                    new Dictionary<string, object?> { ["sid"] = sessionId, ["expectedSystem"] = storedSystem, ["actualSystem"] = systemLower });
            }

            var authExpiration = CertUtils.ValidateCertificateExpiration(authPublicCert);
            if (authExpiration is null)
            {
                return Fail(Route, 500, "AUTH_CERT_EXP_VALIDATION_FAIL", "Failed to validate authentication certificate expiration", Props(sessionId));
            }
            if (authExpiration.IsExpired)
            {
                var expiredAt = IsoTime.From(authExpiration.NotAfter);
                return Fail(Route, 403, "AUTH_CERT_EXPIRED", "Authentication certificate has expired",
                    new Dictionary<string, object?> { ["sid"] = sessionId, ["expiredAt"] = expiredAt }, expiredAt: expiredAt);
            }
            if (authExpiration.IsNotYetValid)
            {
                var validFrom = IsoTime.From(authExpiration.NotBefore);
                return Fail(Route, 403, "AUTH_CERT_NOT_YET_VALID", "Authentication certificate is not yet valid",
                    new Dictionary<string, object?> { ["sid"] = sessionId, ["validFrom"] = validFrom }, validFrom: validFrom);
            }

            var authPublicKey = CertUtils.ExtractPublicKeyFromCert(authPublicCert);
            if (authPublicKey is null)
            {
                return Fail(Route, 500, "AUTH_PUBKEY_EXTRACT_FAIL", "Failed to extract public key from authentication certificate", Props(sessionId));
            }

            if (!CryptoUtils.VerifySignatureEC(payload, signature, authPublicKey))
            {
                return Fail(Route, 401, "PAYLOAD_SIGNATURE_INVALID", "Invalid signature", Props(sessionId));
            }

            var thumbprint = CertUtils.ComputeCertThumbprint(authPublicCert);
            if (thumbprint is null)
            {
                return Fail(Route, 500, "THUMBPRINT_COMPUTE_FAIL", "Failed to compute certificate thumbprint", Props(sessionId));
            }

            var certSnapshot = await CertStore.GetCertificateAsync(Ctx, thumbprint);
            var certRecord = certSnapshot?.Value;
            if (certRecord is null)
            {
                ApiTelemetry.TrackApiFail(Logger, Route, "CERT_NOT_REGISTERED", 200, new Dictionary<string, object?>
                {
                    ["sid"] = sessionId,
                    ["thumbprint"] = thumbprint,
                    ["clientId"] = clientId.Trim(),
                    ["system"] = systemLower,
                });
                return Ok(new AttestationVerifyResult { Exists = false });
            }

            if (certRecord.ClientId != clientId.Trim())
            {
                return Fail(Route, 401, "CERT_CLIENT_ID_MISMATCH", "Certificate clientId mismatch",
                    new Dictionary<string, object?> { ["sid"] = sessionId, ["thumbprint"] = thumbprint, ["expectedClientId"] = certRecord.ClientId, ["actualClientId"] = clientId.Trim() });
            }
            if (certRecord.System != systemLower)
            {
                return Fail(Route, 401, "CERT_SYSTEM_MISMATCH", "Certificate system mismatch",
                    new Dictionary<string, object?> { ["sid"] = sessionId, ["thumbprint"] = thumbprint, ["expectedSystem"] = certRecord.System, ["actualSystem"] = systemLower });
            }

            Func<Task<bool>>? commitAssertion = null;
            if (systemLower == "ios")
            {
                var check = await IosAssertionCheck.CheckAsync(Ctx, new IosAssertionCheckArgs(
                    Route, sessionId, thumbprint, Encoding.UTF8.GetBytes(payload), assertion));
                if (!check.Ok)
                {
                    return check.Result!;
                }
                commitAssertion = check.Commit;
            }

            if (JsonHelpers.GetBool(data, "serverKeyGenerated"))
            {
                return Fail(Route, 409, "SERVER_KEYS_ALREADY_GENERATED", "Server keys already generated for this session", Props(sessionId));
            }

            var encExpiration = CertUtils.ValidateCertificateExpiration(encryptionPublicCert);
            if (encExpiration is null)
            {
                return Fail(Route, 500, "ENC_CERT_EXP_VALIDATION_FAIL", "Failed to validate encryption certificate expiration", Props(sessionId));
            }
            if (encExpiration.IsExpired)
            {
                var expiredAt = IsoTime.From(encExpiration.NotAfter);
                return Fail(Route, 403, "ENC_CERT_EXPIRED", "Encryption certificate has expired",
                    new Dictionary<string, object?> { ["sid"] = sessionId, ["expiredAt"] = expiredAt }, expiredAt: expiredAt);
            }
            if (encExpiration.IsNotYetValid)
            {
                var validFrom = IsoTime.From(encExpiration.NotBefore);
                return Fail(Route, 403, "ENC_CERT_NOT_YET_VALID", "Encryption certificate is not yet valid",
                    new Dictionary<string, object?> { ["sid"] = sessionId, ["validFrom"] = validFrom }, validFrom: validFrom);
            }

            var clientEncryptionPublicKey = CertUtils.ExtractPublicKeyFromCert(encryptionPublicCert);
            if (clientEncryptionPublicKey is null)
            {
                return Fail(Route, 500, "ENC_PUBKEY_EXTRACT_FAIL", "Failed to extract public key from encryption certificate", Props(sessionId));
            }

            var keyPair = CryptoUtils.GenerateServerKeyPairEC();
            if (keyPair is null)
            {
                return Fail(Route, 500, "SERVER_KEYPAIR_GEN_FAIL", "Failed to generate server key pair", Props(sessionId));
            }

            data["serverKeyGenerated"] = true;
            data["serverEncryptionPrivateKey"] = keyPair.PrivateKey;
            data["serverEncryptionPublicKey"] = keyPair.PublicKey;
            data["clientAuthPublicKey"] = authPublicKey;
            data["clientEncryptionPublicKey"] = clientEncryptionPublicKey;
            data["certRegistered"] = true;
            data["clientAuthCertThumbprint"] = thumbprint;

            if (commitAssertion is not null && !await commitAssertion())
            {
                return Fail(Route, 409, "ASSERTION_STATE_CONFLICT", "Certificate changed or expired; obtain a fresh assertion");
            }
            var updated = await ServerUtils.UpdateSessionDataAsync(Ctx, sessionId, sessionData.Token, data, sessionData.Version);
            if (!updated)
            {
                return Fail(Route, 500, "UPDATE_SESSION_FAIL", "Failed to update session with server keys", Props(sessionId));
            }

            return Ok(new AttestationVerifyResult { Exists = true, ServerEncryptionPublicKey = keyPair.PublicKey });
        }
        catch (Azure.AI.Vision.Face.DeviceAttestation.Storage.StorageException) { throw; }
        catch (Exception ex)
        {
            return Fail(Route, 500, "STORAGE_ERROR", "Storage operation failed",
                new Dictionary<string, object?> { ["sid"] = sessionId, ["errorMessage"] = ex.Message });
        }
    }
}
