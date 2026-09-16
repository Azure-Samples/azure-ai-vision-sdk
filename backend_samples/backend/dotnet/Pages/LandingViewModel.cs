namespace FaceLivenessAttestationBackendSample.Pages;

/// <summary>View data for the fallback landing page (QR + open-in-app + result polling).</summary>
public sealed class LandingViewModel
{
    public required string SessionId { get; init; }
    public required string Platform { get; init; }
    public required string QrDataUri { get; init; }
    public string? ActionUrl { get; init; }
    public required string ResultPath { get; init; }
    public required string SessionIdParam { get; init; }
}
