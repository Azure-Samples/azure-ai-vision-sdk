using System.Diagnostics;
using System.Net.Http;
using Google;
using Google.Apis.Auth.OAuth2;
using Google.Apis.PlayIntegrity.v1;
using Google.Apis.PlayIntegrity.v1.Data;
using Google.Apis.Services;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Models;

namespace Azure.AI.Vision.Face.DeviceAttestation.Android;

/// <summary>
/// Outcome of a Play Integrity decode attempt. <see cref="Tolerable"/> is true only
/// for the Google-unavailable (network / HTTP 5xx) or quota-exceeded (HTTP 429)
/// conditions the <c>AllowAndroidAttestationWhenGoogleUnavailable</c> policy may
/// accept; every other failure fails closed.
/// </summary>
internal sealed class IntegrityVerdictResult
{
    public bool Ok { get; private init; }
    public PlayIntegrityVerdict? Verdict { get; private init; }
    public bool Tolerable { get; private init; }
    public string Reason { get; private init; } = "";

    public static IntegrityVerdictResult Success(PlayIntegrityVerdict verdict) => new() { Ok = true, Verdict = verdict, Reason = "ok" };
    public static IntegrityVerdictResult FailTolerable(string reason) => new() { Tolerable = true, Reason = reason };
    public static IntegrityVerdictResult FailHard(string reason) => new() { Reason = reason };
}

/// <summary>
/// The network/credentials boundary for Google's Play Integrity API. Reads the
/// service-account JSON + package name from config and decodes a client-supplied
/// integrity token into an <see cref="IntegrityVerdictResult"/>.
/// </summary>
internal static class PlayIntegrityApi
{
    private const string Scope = "https://www.googleapis.com/auth/playintegrity";

    public static async Task<IntegrityVerdictResult> DecryptAndVerifyIntegrityVerdictAsync(
        AttestationConfig config,
        IAttestationLogger logger,
        string integrityToken)
    {
        var serviceAccountJson = config.GoogleServiceAccountJson;
        if (string.IsNullOrEmpty(serviceAccountJson))
        {
            logger.TrackEvent("AndroidAuth.PlayIntegrity.ConfigMissing", new Dictionary<string, object?> { ["missing"] = "GOOGLE_SERVICE_ACCOUNT_JSON" });
            return IntegrityVerdictResult.FailHard("service_account_not_configured");
        }
        var packageName = config.AndroidPackageName;
        if (string.IsNullOrEmpty(packageName))
        {
            logger.TrackEvent("AndroidAuth.PlayIntegrity.ConfigMissing", new Dictionary<string, object?> { ["missing"] = "ANDROID_PACKAGE_NAME" });
            return IntegrityVerdictResult.FailHard("package_name_not_configured");
        }

        GoogleCredential credential;
        try
        {
            credential = CredentialFactory
                .FromJson<ServiceAccountCredential>(serviceAccountJson)
                .ToGoogleCredential()
                .CreateScoped(Scope);
        }
        catch (Exception e)
        {
            logger.TrackException(e, new Dictionary<string, object?> { ["source"] = "decryptAndVerifyIntegrityVerdict.parseCreds" });
            return IntegrityVerdictResult.FailHard("service_account_parse_error");
        }

        var sw = Stopwatch.StartNew();
        try
        {
            using var service = new PlayIntegrityService(new BaseClientService.Initializer
            {
                HttpClientInitializer = credential,
                ApplicationName = "AzureAIVisionFaceDeviceAttestation",
            });
            var request = service.V1.DecodeIntegrityToken(new DecodeIntegrityTokenRequest { IntegrityToken = integrityToken }, packageName);
            var response = await request.ExecuteAsync();
            sw.Stop();

            if (response?.TokenPayloadExternal is null)
            {
                logger.TrackDependency(Dependency(packageName, sw.Elapsed, success: false, "empty-response"));
                return IntegrityVerdictResult.FailHard("empty_response");
            }

            var verdict = Map(response.TokenPayloadExternal);
            logger.TrackDependency(Dependency(packageName, sw.Elapsed, success: true, "200"));
            return IntegrityVerdictResult.Success(verdict);
        }
        catch (Exception e)
        {
            sw.Stop();
            logger.TrackDependency(Dependency(packageName, sw.Elapsed, success: false, "exception"));
            logger.TrackException(e, new Dictionary<string, object?> { ["source"] = "decryptAndVerifyIntegrityVerdict", ["packageName"] = packageName });
            var kind = ClassifyUnavailable(e);
            return kind is not null ? IntegrityVerdictResult.FailTolerable(kind) : IntegrityVerdictResult.FailHard("api_error");
        }
    }

    /// <summary>
    /// Classify a decode error as a tolerable Google-unavailability condition
    /// ("server_unavailable" for a network error / HTTP 5xx, "quota_exceeded" for
    /// HTTP 429) or null when it must fail closed.
    /// </summary>
    private static string? ClassifyUnavailable(Exception e)
    {
        if (e is GoogleApiException gae)
        {
            int status = (int)gae.HttpStatusCode;
            if (status == 429) return "quota_exceeded";
            if (status >= 500 && status <= 599) return "server_unavailable";
            return null;
        }
        if (e is HttpRequestException) return "server_unavailable";
        if (e is TaskCanceledException || e is OperationCanceledException || e is TimeoutException) return "server_unavailable";
        return null;
    }

    private static DependencyTelemetry Dependency(string packageName, TimeSpan duration, bool success, string resultCode) => new()
    {
        Name = "PlayIntegrity.decodeIntegrityToken",
        Target = "playintegrity.googleapis.com",
        Data = $"packageName={packageName}",
        Duration = duration,
        Success = success,
        ResultCode = resultCode,
        Properties = new Dictionary<string, object?> { ["packageName"] = packageName },
    };

    private static PlayIntegrityVerdict Map(TokenPayloadExternal t) => new()
    {
        RequestDetails = t.RequestDetails is null ? null : new PlayIntegrityRequestDetails
        {
            RequestPackageName = t.RequestDetails.RequestPackageName,
            TimestampMillis = t.RequestDetails.TimestampMillis?.ToString(),
            RequestHash = t.RequestDetails.RequestHash,
        },
        AccountDetails = t.AccountDetails is null ? null : new PlayIntegrityAccountDetails
        {
            AppLicensingVerdict = t.AccountDetails.AppLicensingVerdict,
        },
        AppIntegrity = t.AppIntegrity is null ? null : new PlayIntegrityAppIntegrity
        {
            AppRecognitionVerdict = t.AppIntegrity.AppRecognitionVerdict,
            PackageName = t.AppIntegrity.PackageName,
            CertificateSha256Digest = t.AppIntegrity.CertificateSha256Digest?.ToList(),
            VersionCode = t.AppIntegrity.VersionCode?.ToString(),
        },
        DeviceIntegrity = t.DeviceIntegrity is null ? null : new PlayIntegrityDeviceIntegrity
        {
            DeviceRecognitionVerdict = t.DeviceIntegrity.DeviceRecognitionVerdict?.ToList(),
            RecentDeviceActivity = t.DeviceIntegrity.RecentDeviceActivity is null ? null : new PlayIntegrityRecentDeviceActivity
            {
                DeviceActivityLevel = t.DeviceIntegrity.RecentDeviceActivity.DeviceActivityLevel,
            },
        },
        EnvironmentDetails = t.EnvironmentDetails is null ? null : new PlayIntegrityEnvironmentDetails
        {
            PlayProtectVerdict = t.EnvironmentDetails.PlayProtectVerdict,
            AppAccessRiskVerdict = t.EnvironmentDetails.AppAccessRiskVerdict is null ? null : new PlayIntegrityAppAccessRiskVerdict
            {
                AppsDetected = t.EnvironmentDetails.AppAccessRiskVerdict.AppsDetected?.ToList(),
            },
        },
    };
}
