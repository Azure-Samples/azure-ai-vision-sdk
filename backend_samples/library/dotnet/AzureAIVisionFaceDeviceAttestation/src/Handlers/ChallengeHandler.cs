using System.Security.Cryptography;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Azure.AI.Vision.Face.DeviceAttestation.Services;

namespace Azure.AI.Vision.Face.DeviceAttestation.Handlers;

/// <summary>
/// POST /api/attestation/challenge — issues a one-time random challenge hash for
/// a session and binds the caller's clientId + system into the session state.
/// </summary>
internal sealed class ChallengeHandler : HandlerBase
{
    public const string Route = "attestation/challenge";

    public ChallengeHandler(AttestationContext ctx) : base(ctx) { }

    public async Task<HandlerOutcome> HandleAsync(AttestationChallengeRequest req)
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

        var sessionData = await ServerUtils.GetSessionDataAsync(Ctx, sessionId);
        if (sessionData is null)
        {
            return Fail(Route, 404, "SESSION_NOT_FOUND", "Session not found", Props(sessionId));
        }

        var data = sessionData.Data;
        if (!string.IsNullOrEmpty(JsonHelpers.GetString(data, "challengeHash")))
        {
            return Fail(Route, 409, "CHALLENGE_ALREADY_EXISTS", "Challenge hash already exists for this session", Props(sessionId));
        }

        var challengeHash = Convert.ToHexString(SHA256.HashData(RandomNumberGenerator.GetBytes(32))).ToLowerInvariant();

        data["challengeHash"] = challengeHash;
        data["clientId"] = clientId.Trim();
        data["system"] = systemLower;

        var updated = await ServerUtils.UpdateSessionDataAsync(Ctx, sessionId, sessionData.Token, data, sessionData.Version);
        if (!updated)
        {
            return Fail(Route, 500, "UPDATE_SESSION_FAIL", "Failed to store challenge", Props(sessionId));
        }

        return Ok(new AttestationChallengeSuccess
        {
            ChallengeHash = challengeHash,
            ClientId = clientId.Trim(),
            System = systemLower,
        });
    }
}
