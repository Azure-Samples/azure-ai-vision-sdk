using Azure.AI.Vision.Face.DeviceAttestation.Android;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

public class AndroidIntegrityChecksTests
{
    private const string Package = "com.example.app";

    private static AttestationConfig Config(bool allowDevice = false, bool allowBasic = false)
        => new() { AndroidPackageName = Package, AllowDeviceIntegrity = allowDevice, AllowBasicIntegrity = allowBasic };

    private static PlayIntegrityVerdict Verdict(string appRecognition, string[] deviceVerdicts, string package = Package)
        => new()
        {
            AppIntegrity = new PlayIntegrityAppIntegrity { AppRecognitionVerdict = appRecognition, PackageName = package },
            DeviceIntegrity = new PlayIntegrityDeviceIntegrity { DeviceRecognitionVerdict = deviceVerdicts },
            EnvironmentDetails = new PlayIntegrityEnvironmentDetails { PlayProtectVerdict = "NO_ISSUES" },
        };

    [Fact]
    public void Evaluate_AcceptsStrongIntegrity()
    {
        var warnings = new List<string>();
        var result = IntegrityChecks.Evaluate(Verdict("PLAY_RECOGNIZED", new[] { "MEETS_STRONG_INTEGRITY" }), Config(), debugMode: false, warnings);
        Assert.True(result.Ok);
        Assert.Empty(warnings);
    }

    [Fact]
    public void Evaluate_RejectsPackageMismatch()
    {
        var result = IntegrityChecks.Evaluate(Verdict("PLAY_RECOGNIZED", new[] { "MEETS_STRONG_INTEGRITY" }, package: "com.evil.app"), Config(), false, new List<string>());
        Assert.False(result.Ok);
        Assert.Equal("PACKAGE_NAME_MISMATCH", result.Reason);
    }

    [Fact]
    public void Evaluate_RejectsUnrecognizedApp()
    {
        var result = IntegrityChecks.Evaluate(Verdict("UNRECOGNIZED_VERSION", new[] { "MEETS_STRONG_INTEGRITY" }), Config(), false, new List<string>());
        Assert.False(result.Ok);
        Assert.Equal("APP_NOT_RECOGNIZED", result.Reason);
    }

    [Fact]
    public void Evaluate_RejectsBasicOnlyByDefault()
    {
        var result = IntegrityChecks.Evaluate(Verdict("PLAY_RECOGNIZED", new[] { "MEETS_BASIC_INTEGRITY" }), Config(), false, new List<string>());
        Assert.False(result.Ok);
        Assert.Equal("DEVICE_INTEGRITY_FAIL", result.Reason);
    }

    [Fact]
    public void Evaluate_AcceptsBasicWhenAllowed_WithWarning()
    {
        var warnings = new List<string>();
        var result = IntegrityChecks.Evaluate(Verdict("PLAY_RECOGNIZED", new[] { "MEETS_BASIC_INTEGRITY" }), Config(allowBasic: true), debugMode: false, warnings);
        Assert.True(result.Ok);
        Assert.Contains(warnings, w => w.Contains("MEETS_BASIC_INTEGRITY"));
    }

    [Fact]
    public void VerifyTimestamp_RejectsOldToken()
    {
        var old = DateTimeOffset.UtcNow.AddMinutes(-10).ToUnixTimeMilliseconds().ToString();
        var verdict = new PlayIntegrityVerdict { RequestDetails = new PlayIntegrityRequestDetails { TimestampMillis = old } };
        Assert.Equal("INTEGRITY_TIMESTAMP_OLD", IntegrityChecks.VerifyTimestamp(verdict).Reason);
    }

    [Fact]
    public void VerifyTimestamp_AcceptsFresh()
    {
        var fresh = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();
        Assert.True(IntegrityChecks.VerifyTimestamp(new PlayIntegrityVerdict { RequestDetails = new PlayIntegrityRequestDetails { TimestampMillis = fresh } }).Ok);
    }

    [Fact]
    public void VerifyTimestamp_RejectsMissingOrInvalid()
    {
        var missing = IntegrityChecks.VerifyTimestamp(new PlayIntegrityVerdict());
        var invalid = IntegrityChecks.VerifyTimestamp(new PlayIntegrityVerdict
        {
            RequestDetails = new PlayIntegrityRequestDetails { TimestampMillis = "not-a-timestamp" },
        });

        Assert.Equal("INTEGRITY_TIMESTAMP_INVALID", missing.Reason);
        Assert.Equal("INTEGRITY_TIMESTAMP_INVALID", invalid.Reason);
    }

    [Fact]
    public void VerifyRequestHash_ChecksLeafThumbprint()
    {
        var leaf = new byte[] { 1, 2, 3, 4 };
        var expected = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(leaf)).ToLowerInvariant();
        var ok = new PlayIntegrityVerdict { RequestDetails = new PlayIntegrityRequestDetails { RequestHash = expected } };
        var bad = new PlayIntegrityVerdict { RequestDetails = new PlayIntegrityRequestDetails { RequestHash = "deadbeef" } };
        Assert.True(IntegrityChecks.VerifyRequestHash(ok, leaf).Ok);
        Assert.Equal("INTEGRITY_REQUEST_HASH_MISMATCH", IntegrityChecks.VerifyRequestHash(bad, leaf).Reason);
    }
}
