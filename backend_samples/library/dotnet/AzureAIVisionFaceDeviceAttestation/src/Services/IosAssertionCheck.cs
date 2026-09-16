using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Handlers;
using Azure.AI.Vision.Face.DeviceAttestation.Ios;

namespace Azure.AI.Vision.Face.DeviceAttestation.Services;

/// <summary>Inputs for the shared iOS assertion check.</summary>
internal sealed record IosAssertionCheckArgs(
    string RouteName,
    string SessionId,
    string Thumbprint,
    byte[] Blob,
    string? Assertion);

/// <summary>Either success (caller continues) or the exact failure outcome to return.</summary>
internal sealed class IosAssertionCheckResult
{
    public bool Ok { get; private init; }
    public HandlerOutcome? Result { get; private init; }

    public Func<Task<bool>>? Commit { get; private init; }

    public static IosAssertionCheckResult Success(Func<Task<bool>> commit) => new() { Ok = true, Commit = commit };

    public static IosAssertionCheckResult Failure(HandlerOutcome result) => new() { Ok = false, Result = result };
}

/// <summary>
/// Shared "verify a fresh App Attest assertion + advance the persisted
/// signCount" helper used by every iOS-aware route after registration
/// (attestation/verify, session/token, liveness/digest).
/// </summary>
internal static class IosAssertionCheck
{
    public static async Task<IosAssertionCheckResult> CheckAsync(AttestationContext ctx, IosAssertionCheckArgs args)
    {
        if (string.IsNullOrEmpty(args.Assertion))
        {
            return IosAssertionCheckResult.Failure(Outcomes.Fail(ctx.Logger, args.RouteName, 401, "MISSING_ASSERTION",
                "Missing iOS App Attest assertion",
                new Dictionary<string, object?> { ["sid"] = args.SessionId, ["thumbprint"] = args.Thumbprint }));
        }

        var certRecord = await ctx.Store.GetCertificateAsync(args.Thumbprint);
        if (certRecord is null)
        {
            return IosAssertionCheckResult.Failure(Outcomes.Fail(ctx.Logger, args.RouteName, 401, "CERT_RECORD_MISSING",
                "Certificate record not found",
                new Dictionary<string, object?> { ["sid"] = args.SessionId, ["thumbprint"] = args.Thumbprint }));
        }

        var verdict = certRecord.Value.Metadata?["appAttestVerdict"] as JsonObject;
        var credCertPem = verdict is not null ? JsonHelpers.GetString(verdict, "credCertPem") : null;
        if (string.IsNullOrEmpty(credCertPem))
        {
            // Pre-change cert records need re-registration; cert TTL bounds this.
            return IosAssertionCheckResult.Failure(Outcomes.Fail(ctx.Logger, args.RouteName, 401, "LEGACY_CERT_NO_CREDCERT_PEM",
                "Certificate predates assertion requirement; re-registration required",
                new Dictionary<string, object?> { ["sid"] = args.SessionId, ["thumbprint"] = args.Thumbprint }));
        }

        var expectedRpIdHash = IosVerifier.GetExpectedIosRpIdHash(ctx.Config);
        if (string.IsNullOrEmpty(expectedRpIdHash))
        {
            return IosAssertionCheckResult.Failure(Outcomes.Fail(ctx.Logger, args.RouteName, 500, "MISSING_IOS_APP_ID",
                "Server misconfigured: IOS_APP_ID not set",
                new Dictionary<string, object?> { ["sid"] = args.SessionId }));
        }

        long lastSignCount = ResolveLastSignCount(certRecord.Value.Metadata, verdict);

        var assertionResult = IosVerifier.VerifyIosOngoingAssertion(
            credCertPem!, args.Blob, args.Assertion!, expectedRpIdHash!, lastSignCount);

        if (!assertionResult.Ok)
        {
            return IosAssertionCheckResult.Failure(Outcomes.Fail(ctx.Logger, args.RouteName, 401, "ASSERTION_VERIFY_FAIL",
                "iOS assertion verification failed",
                new Dictionary<string, object?>
                {
                    ["sid"] = args.SessionId,
                    ["thumbprint"] = args.Thumbprint,
                    ["reason"] = assertionResult.Reason,
                    ["message"] = assertionResult.Message,
                    ["signCount"] = assertionResult.SignCount,
                    ["lastSignCount"] = lastSignCount,
                }));
        }

        return IosAssertionCheckResult.Success(() => CertStore.UpdateCertificateMetadataAsync(ctx, args.Thumbprint,
            new JsonObject { ["lastAssertionSignCount"] = assertionResult.SignCount }, certRecord));
    }

    private static long ResolveLastSignCount(JsonObject? metadata, JsonObject? verdict)
    {
        if (metadata is not null && JsonHelpers.GetLong(metadata, "lastAssertionSignCount") is { } persisted)
        {
            return persisted;
        }
        var assertion = verdict?["assertion"] as JsonObject;
        if (assertion is not null && JsonHelpers.GetLong(assertion, "signCount") is { } fromVerdict)
        {
            return fromVerdict;
        }
        return 0;
    }
}
