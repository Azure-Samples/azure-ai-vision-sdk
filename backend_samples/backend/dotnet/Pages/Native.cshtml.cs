using Azure.AI.Vision.Face.DeviceAttestation;
using FaceLivenessAttestationBackendSample.Configuration;
using FaceLivenessAttestationBackendSample.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using Microsoft.Extensions.Options;

namespace FaceLivenessAttestationBackendSample.Pages;

/// <summary>
/// Fallback landing page shown to a device without the app installed: a QR code,
/// an "open in app" store link, and JavaScript that polls the result endpoint.
/// </summary>
public class NativeModel : PageModel
{
    private readonly SessionLanding _landing;
    private readonly AttestationService _attestation;
    private readonly ApiRoutes _routes;

    public NativeModel(
        SessionLanding landing,
        AttestationService attestation,
        IOptions<ApiRoutes> routes)
    {
        _landing = landing;
        _attestation = attestation;
        _routes = routes.Value;
    }

    public LandingViewModel? Landing { get; private set; }
    public string? Error { get; private set; }

    public async Task<IActionResult> OnGetAsync(string? s)
    {
        if (string.IsNullOrWhiteSpace(s))
        {
            Error = "This liveness session doesn't exist or has expired. Start a new session and try again.";
            return Page();
        }
        if (!await _attestation.SessionExistsAsync(s))
        {
            Error = "This liveness session doesn't exist or has expired. Start a new session and try again.";
            return Page();
        }

        var platform = SessionLanding.DetectPlatform(Request.Headers.UserAgent.ToString());
        var publicOrigin = SessionLanding.GetPublicOrigin(Request);
        var links = _landing.BuildLinks(publicOrigin, s, platform, _routes.SessionIdParam);
        ViewData["SmartBanner"] = _landing.BuildSmartBannerContent(s);
        Landing = new LandingViewModel
        {
            SessionId = s,
            Platform = platform,
            QrDataUri = SessionLanding.BuildQrDataUri(links.QrUrl),
            ActionUrl = links.ActionUrl,
            ResultPath = _routes.SessionResult,
            SessionIdParam = _routes.SessionIdParam,
        };
        return Page();
    }
}

/// <summary>Same landing content, served at /result (the app-open callback fallback).</summary>
public sealed class ResultModel : NativeModel
{
    public ResultModel(
        SessionLanding landing,
        AttestationService attestation,
        IOptions<ApiRoutes> routes)
        : base(landing, attestation, routes) { }
}
