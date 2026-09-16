namespace Azure.AI.Vision.Face.DeviceAttestation.Configuration;

/// <summary>
/// Attestation runtime configuration. The library is configured ONCE with an
/// <see cref="AttestationConfig"/> when the <see cref="AttestationService"/> is
/// created, and never reads environment variables itself — so it can be reused
/// in any host that supplies these values. The host sources them (from env, a
/// secret store, etc.).
/// </summary>
public sealed class AttestationConfig
{
    /// <summary>Max accepted client certificate size in bytes (default 10240).</summary>
    public int MaxCertSize { get; init; } = 10240;

    // --- Android Play Integrity ---

    /// <summary>Expected Android application id.</summary>
    public string AndroidPackageName { get; init; } = "";

    /// <summary>Google service-account JSON for the Play Integrity API.</summary>
    public string GoogleServiceAccountJson { get; init; } = "";

    /// <summary>Loosen recognition/integrity requirements for dev/testing.</summary>
    public bool DebugMode { get; init; }

    /// <summary>Accept MEETS_DEVICE_INTEGRITY verdicts.</summary>
    public bool AllowDeviceIntegrity { get; init; }

    /// <summary>Accept MEETS_BASIC_INTEGRITY verdicts.</summary>
    public bool AllowBasicIntegrity { get; init; }

    /// <summary>Optional: allow Android attestation to pass on hardware Key Attestation alone when the Play Integrity API is unavailable / quota-exceeded (default false = fail closed).</summary>
    public bool AllowAndroidAttestationWhenGoogleUnavailable { get; init; }

    // --- iOS App Attest ---

    /// <summary>iOS App Attest identity "&lt;TeamID&gt;.&lt;BundleID&gt;".</summary>
    public string IosAppId { get; init; } = "";

    // --- Well-known / app-link binding ---

    /// <summary>iOS AASA appID; falls back to <see cref="IosAppId"/> when unset.</summary>
    public string? IosApplinkAppId { get; init; }

    /// <summary>Optional App Clip appID "&lt;TeamID&gt;.&lt;BundleID&gt;.Clip".</summary>
    public string? IosAppClipId { get; init; }

    /// <summary>Android signing-cert SHA-256 fingerprints for assetlinks.</summary>
    public IReadOnlyList<string> AndroidSha256CertFingerprints { get; init; } = Array.Empty<string>();

    /// <summary>Universal/App Link path pattern (default /native*).</summary>
    public string ApplinkPath { get; init; } = "/native*";
}
