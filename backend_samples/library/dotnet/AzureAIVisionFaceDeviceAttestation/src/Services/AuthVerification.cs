using Azure.AI.Vision.Face.DeviceAttestation.Android;
using Azure.AI.Vision.Face.DeviceAttestation.Ios;

namespace Azure.AI.Vision.Face.DeviceAttestation.Services;

/// <summary>Parsed message inputs for attestation verification.</summary>
internal sealed class AttestationMessageData
{
    public string? ChallengeHash { get; init; }
    public required string ClientId { get; init; }
    public required string System { get; init; }
    /// <summary>PEM-encoded leaf certificate.</summary>
    public string? PublicCert { get; init; }
}

/// <summary>Platform-agnostic result of an attestation verification.</summary>
internal sealed class AuthVerificationResult
{
    public bool Verified { get; init; }
    public string Platform { get; init; } = "unknown";
    public string Message { get; init; } = "";
    public string Timestamp { get; init; } = "";
    public int? ChainLength { get; init; }
    public string? RootCA { get; init; }
    public object? IntegrityVerdict { get; init; }
    public object? AppAttestVerdict { get; init; }
    public string? LeafCertValidityWarning { get; init; }
    public IReadOnlyList<string>? Warnings { get; init; }
}

/// <summary>Dispatches attestation verification to the iOS or Android verifier.</summary>
internal static class AuthVerification
{
    public static async Task<AuthVerificationResult> VerifyAuthBySystemAsync(
        AttestationContext ctx,
        AttestationMessageData messageData,
        string attestJson)
    {
        var systemLower = messageData.System.ToLowerInvariant();

        ctx.Logger.TrackEvent("AuthVerification.Dispatch", new Dictionary<string, object?>
        {
            ["platform"] = systemLower,
            ["clientId"] = messageData.ClientId,
            ["attestJsonLength"] = attestJson.Length,
        });

        if (systemLower == "ios")
        {
            return await IosVerifier.VerifyAsync(ctx, messageData, attestJson);
        }
        if (systemLower == "android")
        {
            return await AndroidVerifier.VerifyAsync(ctx, messageData, attestJson);
        }

        ctx.Logger.TrackEvent("AuthVerification.UnsupportedSystem", new Dictionary<string, object?>
        {
            ["platform"] = messageData.System,
            ["clientId"] = messageData.ClientId,
        });
        return new AuthVerificationResult
        {
            Verified = false,
            Platform = "unknown",
            Message = $"Unsupported system: {messageData.System}",
            Timestamp = IsoTime.Now(),
        };
    }
}
