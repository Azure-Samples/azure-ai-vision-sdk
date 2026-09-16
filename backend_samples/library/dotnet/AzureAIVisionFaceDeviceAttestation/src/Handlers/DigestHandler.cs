using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Azure.AI.Vision.Face.DeviceAttestation.Services;

namespace Azure.AI.Vision.Face.DeviceAttestation.Handlers;

/// <summary>
/// POST /api/liveness/digest — accepts the client's signed + encrypted liveness
/// digest: verifies the auth-cert signature (and, on iOS, a fresh assertion),
/// decrypts the payload, checks clientId/os against the session, records the
/// digest, and returns an encrypted acknowledgement.
/// </summary>
internal sealed class DigestHandler : HandlerBase
{
    public const string Route = "liveness/digest";

    public DigestHandler(AttestationContext ctx) : base(ctx) { }

    public async Task<HandlerOutcome> HandleAsync(LivenessDigestRequest req)
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

        var requestBody = req.Body;
        if (requestBody is null)
        {
            return Fail(Route, 400, "INVALID_JSON_BODY", "Invalid JSON body", Props(sessionId));
        }
        if (string.IsNullOrEmpty(requestBody.EncryptedData))
        {
            return Fail(Route, 400, "MISSING_ENCRYPTED_DATA", "Missing or invalid \"encryptedData\" field (expected base64 string)", Props(sessionId));
        }
        if (string.IsNullOrEmpty(requestBody.Signature))
        {
            return Fail(Route, 400, "MISSING_SIGNATURE", "Missing or invalid \"signature\" field", Props(sessionId));
        }

        var sessionData = await ServerUtils.GetSessionDataAsync(Ctx, sessionId);
        if (sessionData is null)
        {
            return Fail(Route, 404, "SESSION_NOT_FOUND", "Session not found", Props(sessionId));
        }

        var data = sessionData.Data;
        if (!JsonHelpers.GetBool(data, "serverKeyGenerated"))
        {
            return Fail(Route, 409, "SERVER_KEYS_NOT_GENERATED", "Server keys not generated. Call /api/attestation/verify or /api/attestation/register first", Props(sessionId));
        }
        if (!JsonHelpers.GetBool(data, "certRegistered"))
        {
            return Fail(Route, 409, "CERT_NOT_REGISTERED", "Certificate not registered. Call /api/attestation/verify or /api/attestation/register first", Props(sessionId));
        }
        if (!JsonHelpers.GetBool(data, "authCompleted"))
        {
            return Fail(Route, 409, "AUTH_NOT_COMPLETED", "Obtain the session token before submitting a digest");
        }
        if (JsonHelpers.GetBool(data, "digestCompleted"))
        {
            return Fail(Route, 409, "DIGEST_ALREADY_EXISTS", "Digest already exists for this session", Props(sessionId));
        }

        var clientAuthPublicKey = JsonHelpers.GetString(data, "clientAuthPublicKey");
        if (string.IsNullOrEmpty(clientAuthPublicKey))
        {
            return Fail(Route, 500, "MISSING_CLIENT_AUTH_PUBKEY", "Client auth public key not found in session", Props(sessionId));
        }

        if (!CryptoUtils.VerifySignatureEC(requestBody.EncryptedData, requestBody.Signature, clientAuthPublicKey))
        {
            return Fail(Route, 401, "SIGNATURE_INVALID", "Signature verification failed", Props(sessionId));
        }

        Func<Task<bool>>? commitAssertion = null;
        if (JsonHelpers.GetString(data, "system") == "ios")
        {
            var thumbprint = JsonHelpers.GetString(data, "clientAuthCertThumbprint");
            if (string.IsNullOrEmpty(thumbprint))
            {
                return Fail(Route, 500, "MISSING_CERT_THUMBPRINT", "Client cert thumbprint not found in session", Props(sessionId));
            }
            var check = await IosAssertionCheck.CheckAsync(Ctx, new IosAssertionCheckArgs(
                Route, sessionId, thumbprint, Encoding.UTF8.GetBytes(requestBody.EncryptedData), requestBody.Assertion));
            if (!check.Ok)
            {
                return check.Result!;
            }
            commitAssertion = check.Commit;
        }

        var serverEncryptionPrivateKey = JsonHelpers.GetString(data, "serverEncryptionPrivateKey");
        if (string.IsNullOrEmpty(serverEncryptionPrivateKey))
        {
            return Fail(Route, 500, "MISSING_SERVER_ENC_PRIVKEY", "Server encryption private key not found in session", Props(sessionId));
        }

        var decryptedPayload = CryptoUtils.DecryptWithPrivateKeyEC(requestBody.EncryptedData, serverEncryptionPrivateKey);
        if (decryptedPayload is null)
        {
            return Fail(Route, 401, "DECRYPTION_FAIL", "Decryption failed", Props(sessionId));
        }

        string? cid, os, digestValue;
        try
        {
            var parsed = JsonNode.Parse(decryptedPayload) as JsonObject;
            cid = parsed is null ? null : JsonHelpers.GetString(parsed, "cid");
            os = parsed is null ? null : JsonHelpers.GetString(parsed, "os");
            digestValue = parsed is null ? null : JsonHelpers.GetString(parsed, "digest");
        }
        catch (JsonException)
        {
            return Fail(Route, 401, "DECRYPTED_PAYLOAD_PARSE_ERROR", "Invalid decrypted payload format", Props(sessionId));
        }

        if (string.IsNullOrEmpty(cid) || cid.Trim().Length == 0)
        {
            return Fail(Route, 400, "MISSING_CID", "Missing or invalid \"cid\" in payload", Props(sessionId));
        }

        var clientId = cid.Trim();
        os ??= "";
        if (string.IsNullOrWhiteSpace(digestValue))
        {
            return Fail(Route, 400, "INVALID_DIGEST", "Expected a nonempty digest");
        }

        var storedClientId = JsonHelpers.GetString(data, "clientId");
        if (!string.IsNullOrEmpty(storedClientId) && storedClientId != clientId)
        {
            return Fail(Route, 401, "CLIENT_ID_MISMATCH", "Client ID mismatch",
                new Dictionary<string, object?> { ["sid"] = sessionId, ["expectedClientId"] = storedClientId, ["actualClientId"] = clientId });
        }
        var storedSystem = JsonHelpers.GetString(data, "system");
        if (!string.IsNullOrEmpty(storedSystem) && storedSystem != os)
        {
            return Fail(Route, 401, "OS_MISMATCH", "Operating system mismatch",
                new Dictionary<string, object?> { ["sid"] = sessionId, ["expectedSystem"] = storedSystem, ["actualOs"] = os });
        }

        data["digest"] = digestValue;
        data["attestationClientId"] = clientId;
        data["attestationOs"] = os;
        data["digestCompleted"] = true;

        var responsePayload = JsonSerializer.Serialize(new { success = true, timestamp = IsoTime.Now() });

        var clientEncryptionPublicKey = JsonHelpers.GetString(data, "clientEncryptionPublicKey");
        if (string.IsNullOrEmpty(clientEncryptionPublicKey))
        {
            return Fail(Route, 500, "MISSING_CLIENT_ENC_PUBKEY", "Client encryption public key not found in session", Props(sessionId));
        }

        var encryptedResponse = CryptoUtils.EncryptWithPublicKeyEC(responsePayload, clientEncryptionPublicKey);
        if (encryptedResponse is null)
        {
            return Fail(Route, 500, "RESPONSE_ENCRYPT_FAIL", "Failed to encrypt response", Props(sessionId));
        }

        if (commitAssertion is not null && !await commitAssertion())
        {
            return Fail(Route, 409, "ASSERTION_STATE_CONFLICT", "Certificate changed or expired; obtain a fresh assertion");
        }
        if (!await ServerUtils.UpdateSessionDataAsync(Ctx, sessionId, sessionData.Token, data, sessionData.Version))
        {
            return Fail(Route, 409, "SESSION_STATE_CONFLICT", "Session changed or expired");
        }
        return Ok(new LivenessDigestSuccess { EncryptedData = encryptedResponse },
            data: new LivenessDigestData { ClientDigest = digestValue });
    }
}
