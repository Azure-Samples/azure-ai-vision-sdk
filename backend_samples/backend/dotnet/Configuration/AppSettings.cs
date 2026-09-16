using Azure.AI.Vision.Face.DeviceAttestation.Configuration;

namespace FaceLivenessAttestationBackendSample.Configuration;

/// <summary>
/// Strongly-typed settings bound from the "AppSettings" configuration section
/// (overridable by environment variables via the <c>AppSettings__Key</c>
/// convention). Mirrors the React sample's environment variables.
/// </summary>
public sealed class AppSettings
{
    // --- Attestation (mapped to AttestationConfig) ---
    public int MaxCertSize { get; set; } = 10240;
    public string AndroidPackageName { get; set; } = "";
    public string GoogleServiceAccountJson { get; set; } = "";
    public bool DebugMode { get; set; }
    public bool AllowDeviceIntegrity { get; set; }
    public bool AllowBasicIntegrity { get; set; }
    public bool AllowAndroidAttestationWhenGoogleUnavailable { get; set; }
    public string IosAppId { get; set; } = "";
    public string? IosApplinkAppId { get; set; }
    public string? IosAppClipId { get; set; }
    public string AndroidSha256CertFingerprints { get; set; } = "";
    public string ApplinkPath { get; set; } = "";

    // --- App-level ---
    public int SessionTokenTtl { get; set; } = 600;
    public int CertTtl { get; set; } = 604800;
    public string FaceApiVersion { get; set; } = "v1.2";
    public bool UseLocalRedis { get; set; }
    public string RedisHostname { get; set; } = "";
    public int RedisPort { get; set; } = 6380;
    public string? IosAppStoreUrl { get; set; }
    public string? AndroidPlayStoreUrl { get; set; }

    /// <summary>Build the injected <see cref="AttestationConfig"/> from these settings.</summary>
    public AttestationConfig ToAttestationConfig() => new()
    {
        MaxCertSize = MaxCertSize,
        AndroidPackageName = AndroidPackageName,
        GoogleServiceAccountJson = GoogleServiceAccountJson,
        DebugMode = DebugMode,
        AllowDeviceIntegrity = AllowDeviceIntegrity,
        AllowBasicIntegrity = AllowBasicIntegrity,
        AllowAndroidAttestationWhenGoogleUnavailable = AllowAndroidAttestationWhenGoogleUnavailable,
        IosAppId = IosAppId,
        IosApplinkAppId = Blank(IosApplinkAppId),
        IosAppClipId = Blank(IosAppClipId),
        AndroidSha256CertFingerprints = SplitFingerprints(AndroidSha256CertFingerprints),
        ApplinkPath = string.IsNullOrWhiteSpace(ApplinkPath) ? "/native*" : ApplinkPath,
    };

    private static string? Blank(string? value) => string.IsNullOrWhiteSpace(value) ? null : value;

    private static IReadOnlyList<string> SplitFingerprints(string raw)
        => string.IsNullOrWhiteSpace(raw)
            ? Array.Empty<string>()
            : raw.Split(new[] { ',', ' ', '\n', '\r', '\t' }, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
}
