namespace Azure.AI.Vision.Face.DeviceAttestation.Models;

/// <summary>Play Integrity requestDetails.</summary>
public sealed class PlayIntegrityRequestDetails
{
    /// <summary>The package name associated with the integrity request.</summary>
    public string? RequestPackageName { get; init; }
    /// <summary>The request timestamp in Unix epoch milliseconds, represented as a decimal string.</summary>
    public string? TimestampMillis { get; init; }
    /// <summary>The application-provided request hash returned in the verdict.</summary>
    public string? RequestHash { get; init; }
}

/// <summary>Play Integrity accountDetails.</summary>
public sealed class PlayIntegrityAccountDetails
{
    /// <summary>The app licensing verdict for the requesting account.</summary>
    public string? AppLicensingVerdict { get; init; }
}

/// <summary>Play Integrity appIntegrity.</summary>
public sealed class PlayIntegrityAppIntegrity
{
    /// <summary>The verdict describing whether Google Play recognizes the app.</summary>
    public string? AppRecognitionVerdict { get; init; }
    /// <summary>The package name recognized by Google Play.</summary>
    public string? PackageName { get; init; }
    /// <summary>The SHA-256 digests of the app's signing certificates.</summary>
    public IReadOnlyList<string>? CertificateSha256Digest { get; init; }
    /// <summary>The app version code recognized by Google Play.</summary>
    public string? VersionCode { get; init; }
}

/// <summary>Play Integrity deviceIntegrity.recentDeviceActivity.</summary>
public sealed class PlayIntegrityRecentDeviceActivity
{
    /// <summary>The recent integrity-token request activity level for the device.</summary>
    public string? DeviceActivityLevel { get; init; }
}

/// <summary>Play Integrity deviceIntegrity.</summary>
public sealed class PlayIntegrityDeviceIntegrity
{
    /// <summary>The device-recognition verdict labels returned by Play Integrity.</summary>
    public IReadOnlyList<string>? DeviceRecognitionVerdict { get; init; }
    /// <summary>Recent integrity-token request activity for the device.</summary>
    public PlayIntegrityRecentDeviceActivity? RecentDeviceActivity { get; init; }
}

/// <summary>Play Integrity environmentDetails.appAccessRiskVerdict.</summary>
public sealed class PlayIntegrityAppAccessRiskVerdict
{
    /// <summary>The categories of apps detected as potential access risks.</summary>
    public IReadOnlyList<string>? AppsDetected { get; init; }
}

/// <summary>Play Integrity environmentDetails.</summary>
public sealed class PlayIntegrityEnvironmentDetails
{
    /// <summary>The Google Play Protect verdict for the device.</summary>
    public string? PlayProtectVerdict { get; init; }
    /// <summary>The verdict describing apps that could capture or control the requesting app.</summary>
    public PlayIntegrityAppAccessRiskVerdict? AppAccessRiskVerdict { get; init; }
}

/// <summary>
/// Decoded Play Integrity verdict. Persisted under the certificate metadata so
/// the post-attestation record retains everything decoded from the token.
/// </summary>
public sealed class PlayIntegrityVerdict
{
    /// <summary>Details that identify and bind the integrity request.</summary>
    public PlayIntegrityRequestDetails? RequestDetails { get; init; }
    /// <summary>The requesting account's licensing details.</summary>
    public PlayIntegrityAccountDetails? AccountDetails { get; init; }
    /// <summary>The requesting app's integrity details.</summary>
    public PlayIntegrityAppIntegrity? AppIntegrity { get; init; }
    /// <summary>The requesting device's integrity details.</summary>
    public PlayIntegrityDeviceIntegrity? DeviceIntegrity { get; init; }
    /// <summary>The device environment and app-access risk details.</summary>
    public PlayIntegrityEnvironmentDetails? EnvironmentDetails { get; init; }

    /// <summary>
    /// Lowercase hex of the attestationChallenge from the leaf's keymaster
    /// extension, populated server-side once verified to equal the session
    /// challengeHash (audit record of the session binding).
    /// </summary>
    public string? AttestationChallenge { get; set; }
}
