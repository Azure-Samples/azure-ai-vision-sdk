using System.Security.Cryptography;
using System.Text;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Services;

namespace Azure.AI.Vision.Face.DeviceAttestation.Ios;

/// <summary>Result of a per-call App Attest assertion verification.</summary>
internal sealed class OngoingAssertionResult
{
    public bool Ok { get; init; }
    public string? Reason { get; init; }
    public string? Message { get; init; }
    public long SignCount { get; init; }
}

/// <summary>
/// iOS App Attest verifier — the seam the handlers depend on. Delegates the
/// attestation + assertion logic to <see cref="AppAttestVerification"/>.
/// </summary>
internal static class IosVerifier
{
    /// <summary>Verify an App Attest attestation (registration).</summary>
    public static Task<AuthVerificationResult> VerifyAsync(AttestationContext ctx, AttestationMessageData messageData, string attestJson)
        => Task.FromResult(AppAttestVerification.Verify(ctx.Config, ctx.Logger, messageData, attestJson));

    /// <summary>Expected rpIdHash: SHA-256 of the configured iOS App ID (lowercase hex).</summary>
    public static string? GetExpectedIosRpIdHash(AttestationConfig config)
    {
        if (string.IsNullOrEmpty(config.IosAppId))
        {
            return null;
        }
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(config.IosAppId))).ToLowerInvariant();
    }

    /// <summary>Verify a fresh per-call assertion against the persisted credCert.</summary>
    public static OngoingAssertionResult VerifyIosOngoingAssertion(
        string credCertPem,
        byte[] blob,
        string assertion,
        string expectedRpIdHash,
        long lastSignCount)
        => AppAttestVerification.VerifyOngoingAssertion(credCertPem, blob, assertion, expectedRpIdHash, lastSignCount);
}
