using System.Security.Cryptography;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Models;

namespace Azure.AI.Vision.Face.DeviceAttestation.Android;

/// <summary>Outcome of an integrity check: ok, or a failure reason + message.</summary>
internal readonly record struct IntegrityCheckResult(bool Ok, string? Reason, string? Message)
{
    public static readonly IntegrityCheckResult Success = new(true, null, null);
    public static IntegrityCheckResult Fail(string reason, string message) => new(false, reason, message);
}

/// <summary>Semantic checks on the decoded Play Integrity verdict.</summary>
internal static class IntegrityChecks
{
    /// <summary>requestHash must equal hex(sha256(leafCertDer)) — binds the token to the auth key.</summary>
    public static IntegrityCheckResult VerifyRequestHash(PlayIntegrityVerdict verdict, byte[] leafCertDer)
    {
        var expected = Convert.ToHexString(SHA256.HashData(leafCertDer)).ToLowerInvariant();
        return verdict.RequestDetails?.RequestHash == expected
            ? IntegrityCheckResult.Success
            : IntegrityCheckResult.Fail("INTEGRITY_REQUEST_HASH_MISMATCH",
                "Play Integrity verification failed: requestHash mismatch (possible replay attack)");
    }

    /// <summary>Tokens older than 5 minutes are rejected.</summary>
    public static IntegrityCheckResult VerifyTimestamp(PlayIntegrityVerdict verdict)
    {
        var timestampMillis = verdict.RequestDetails?.TimestampMillis;
        if (string.IsNullOrEmpty(timestampMillis) || !long.TryParse(timestampMillis, out var tokenTimestamp))
        {
            return IntegrityCheckResult.Fail("INTEGRITY_TIMESTAMP_INVALID",
                "Play Integrity verification failed: token timestamp missing or invalid");
        }
        var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        return Math.Abs(now - tokenTimestamp) > 5 * 60 * 1000
            ? IntegrityCheckResult.Fail("INTEGRITY_TIMESTAMP_OLD", "Play Integrity verification failed: token timestamp too old")
            : IntegrityCheckResult.Success;
    }

    /// <summary>
    /// Validate appIntegrity (package name + appRecognitionVerdict) and
    /// deviceIntegrity (STRONG / DEVICE / BASIC selectable via config), and
    /// collect non-blocking environment warnings.
    /// </summary>
    public static IntegrityCheckResult Evaluate(PlayIntegrityVerdict verdict, AttestationConfig config, bool debugMode, List<string> warnings)
    {
        var appIntegrity = verdict.AppIntegrity;
        var deviceIntegrity = verdict.DeviceIntegrity;
        var environmentDetails = verdict.EnvironmentDetails;

        var expectedPackageName = config.AndroidPackageName;
        if (string.IsNullOrEmpty(expectedPackageName))
        {
            return IntegrityCheckResult.Fail("MISSING_PACKAGE_NAME_ENV", "ANDROID_PACKAGE_NAME environment variable not set");
        }
        if (appIntegrity?.PackageName != expectedPackageName)
        {
            return IntegrityCheckResult.Fail("PACKAGE_NAME_MISMATCH", "Play Integrity verification failed: package name mismatch");
        }

        if (appIntegrity?.AppRecognitionVerdict != "PLAY_RECOGNIZED")
        {
            if (debugMode && appIntegrity?.AppRecognitionVerdict == "UNRECOGNIZED_VERSION")
            {
                warnings.Add($"App recognition warning (debug mode): {appIntegrity?.AppRecognitionVerdict}");
            }
            else
            {
                return IntegrityCheckResult.Fail("APP_NOT_RECOGNIZED",
                    $"Play Integrity verification failed: app not recognized ({appIntegrity?.AppRecognitionVerdict})");
            }
        }

        var deviceVerdicts = deviceIntegrity?.DeviceRecognitionVerdict ?? Array.Empty<string>();
        bool hasStrongIntegrity = deviceVerdicts.Contains("MEETS_STRONG_INTEGRITY");
        bool allowDeviceIntegrity = config.AllowDeviceIntegrity || debugMode;
        bool hasDeviceIntegrity = allowDeviceIntegrity && deviceVerdicts.Contains("MEETS_DEVICE_INTEGRITY");
        bool allowBasicIntegrity = config.AllowBasicIntegrity || debugMode;
        bool hasBasicIntegrity = allowBasicIntegrity && deviceVerdicts.Contains("MEETS_BASIC_INTEGRITY");

        if (!hasStrongIntegrity && !hasDeviceIntegrity && !hasBasicIntegrity)
        {
            return IntegrityCheckResult.Fail("DEVICE_INTEGRITY_FAIL",
                "Play Integrity verification failed: device does not meet integrity requirements");
        }

        if (!hasStrongIntegrity && hasDeviceIntegrity)
        {
            warnings.Add($"Device integrity warning{(debugMode ? " (debug mode)" : "")}: MEETS_DEVICE_INTEGRITY only, not MEETS_STRONG_INTEGRITY");
        }
        else if (!hasStrongIntegrity && hasBasicIntegrity)
        {
            warnings.Add($"Device integrity warning{(debugMode ? " (debug mode)" : "")}: MEETS_BASIC_INTEGRITY only, not MEETS_STRONG_INTEGRITY");
        }

        if (environmentDetails?.PlayProtectVerdict != "NO_ISSUES")
        {
            warnings.Add($"Play Protect verdict: {environmentDetails?.PlayProtectVerdict}");
        }

        var appsDetected = environmentDetails?.AppAccessRiskVerdict?.AppsDetected;
        if (appsDetected is { Count: > 0 })
        {
            warnings.Add($"App Access Risk - detected apps: {string.Join(", ", appsDetected)}");
        }

        return IntegrityCheckResult.Success;
    }
}
