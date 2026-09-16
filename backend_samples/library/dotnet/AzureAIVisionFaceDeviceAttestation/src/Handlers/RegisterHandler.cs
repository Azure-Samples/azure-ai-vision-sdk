using System.Text.Json;
using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Azure.AI.Vision.Face.DeviceAttestation.Services;

namespace Azure.AI.Vision.Face.DeviceAttestation.Handlers;

/// <summary>
/// POST /api/attestation/register — verifies the platform attestation (Play
/// Integrity / App Attest), persists the client's auth certificate, generates
/// the server EC key pair, and stores the exchanged public keys on the session.
/// </summary>
internal sealed class RegisterHandler : HandlerBase
{
    public const string Route = "attestation/register";

    public RegisterHandler(AttestationContext ctx) : base(ctx) { }

    public async Task<HandlerOutcome> HandleAsync(AttestationRegisterRequest req)
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
        string? attestJson;
        try
        {
            var parsed = JsonNode.Parse(payload) as JsonObject;
            challengeHash = parsed is null ? null : JsonHelpers.GetString(parsed, "challengeHash");
            encryptionPublicCert = parsed is null ? null : JsonHelpers.GetString(parsed, "encryptionPublicCert");
            attestJson = parsed is null ? null : JsonHelpers.GetString(parsed, "attestJson");
            if (string.IsNullOrEmpty(challengeHash) || string.IsNullOrEmpty(encryptionPublicCert) || string.IsNullOrEmpty(attestJson))
            {
                return Fail(Route, 400, "MISSING_PAYLOAD_FIELDS", "Payload must contain challengeHash, encryptionPublicCert, and attestJson", Props(sessionId));
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
            var storedClientId = JsonHelpers.GetString(data, "clientId");
            var storedSystem = JsonHelpers.GetString(data, "system");
            if (string.IsNullOrEmpty(storedChallengeHash) || string.IsNullOrEmpty(storedClientId) || string.IsNullOrEmpty(storedSystem))
            {
                return Fail(Route, 409, "SESSION_NOT_INITIALIZED", "Session not initialized. Call /api/attestation/challenge first", Props(sessionId));
            }

            if (challengeHash != storedChallengeHash)
            {
                return Fail(Route, 401, "CHALLENGE_HASH_MISMATCH", "Invalid challenge hash", Props(sessionId));
            }

            if (clientId.Trim() != storedClientId || systemLower != storedSystem)
            {
                return Fail(Route, 401, "CLIENT_OR_SYSTEM_MISMATCH", "Client ID or system mismatch",
                    new Dictionary<string, object?>
                    {
                        ["sid"] = sessionId,
                        ["requestClientId"] = clientId.Trim(),
                        ["sessionClientId"] = storedClientId,
                        ["requestSystem"] = systemLower,
                        ["sessionSystem"] = storedSystem,
                    });
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

            var certValidation = CertUtils.ValidateCertificate(authPublicCert);
            if (certValidation is null || !certValidation.Valid)
            {
                return Fail(Route, 400, "AUTH_CERT_STRUCTURE_INVALID", "Invalid authentication certificate", Props(sessionId));
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

            var messageData = new AttestationMessageData
            {
                ChallengeHash = storedChallengeHash,
                ClientId = storedClientId,
                System = storedSystem,
                PublicCert = authPublicCert,
            };

            var verificationResult = await AuthVerification.VerifyAuthBySystemAsync(Ctx, messageData, attestJson);
            if (!verificationResult.Verified)
            {
                return Fail(Route, 401, "ATTESTATION_VERIFICATION_FAIL", "Attestation verification failed",
                    new Dictionary<string, object?>
                    {
                        ["sid"] = sessionId,
                        ["platform"] = verificationResult.Platform,
                        ["verificationMessage"] = verificationResult.Message,
                    });
            }

            var thumbprint = CertUtils.ComputeCertThumbprint(authPublicCert);
            if (thumbprint is null)
            {
                return Fail(Route, 500, "THUMBPRINT_COMPUTE_FAIL", "Failed to compute certificate thumbprint", Props(sessionId));
            }

            var metadata = new JsonObject
            {
                ["verificationTimestamp"] = verificationResult.Timestamp,
                ["attestJson"] = attestJson,
            };
            if (verificationResult.IntegrityVerdict is not null)
            {
                metadata["integrityVerdict"] = JsonSerializer.SerializeToNode(verificationResult.IntegrityVerdict, LibraryJson.Metadata);
            }
            if (verificationResult.AppAttestVerdict is not null)
            {
                metadata["appAttestVerdict"] = JsonSerializer.SerializeToNode(verificationResult.AppAttestVerdict, LibraryJson.Metadata);
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

            var saveResult = await CertStore.SaveCertificateAsync(Ctx, clientId.Trim(), systemLower, authPublicCert, metadata);
            if (saveResult is null)
            {
                return Fail(Route, 409, "SAVE_CERT_FAIL", "Certificate changed or could not be created");
            }

            data["serverKeyGenerated"] = true;
            data["serverEncryptionPrivateKey"] = keyPair.PrivateKey;
            data["serverEncryptionPublicKey"] = keyPair.PublicKey;
            data["clientAuthPublicKey"] = authPublicKey;
            data["clientEncryptionPublicKey"] = clientEncryptionPublicKey;
            data["certRegistered"] = true;
            data["clientAuthCertThumbprint"] = saveResult.Thumbprint;

            var updated = await ServerUtils.UpdateSessionDataAsync(Ctx, sessionId, sessionData.Token, data, sessionData.Version);
            if (!updated)
            {
                return Fail(Route, 500, "UPDATE_SESSION_FAIL", "Failed to update session with server keys", Props(sessionId));
            }

            var registerData = systemLower == "ios"
                ? new AttestationRegisterData
                {
                    Platform = verificationResult.Platform,
                    AppAttestVerdict = verificationResult.AppAttestVerdict,
                    Warnings = verificationResult.Warnings,
                }
                : new AttestationRegisterData
                {
                    Platform = verificationResult.Platform,
                    AndroidKeyAttestation = new AndroidKeyAttestationInfo
                    {
                        ChainLength = verificationResult.ChainLength,
                        RootCA = verificationResult.RootCA,
                        LeafCertValidityWarning = verificationResult.LeafCertValidityWarning,
                    },
                    IntegrityVerdict = verificationResult.IntegrityVerdict,
                    Warnings = verificationResult.Warnings,
                };

            return Ok(
                new AttestationRegisterSuccess
                {
                    Message = "Certificate stored successfully",
                    ServerEncryptionPublicKey = keyPair.PublicKey,
                },
                data: registerData);
        }
        catch (Azure.AI.Vision.Face.DeviceAttestation.Storage.StorageException) { throw; }
        catch (Exception ex)
        {
            ApiTelemetry.TrackApiFail(Logger, Route, "INTERNAL_ERROR", 500, new Dictionary<string, object?>
            {
                ["sid"] = sessionId,
                ["errorMessage"] = ex.Message,
            });
            throw;
        }
    }
}
