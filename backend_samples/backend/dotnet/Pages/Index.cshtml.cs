using Azure.AI.Vision.Face.DeviceAttestation;
using FaceLivenessAttestationBackendSample.Configuration;
using FaceLivenessAttestationBackendSample.Services;
using FaceLivenessAttestationBackendSample.Storage;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using Microsoft.Extensions.Options;

namespace FaceLivenessAttestationBackendSample.Pages;

public sealed class IndexModel : PageModel
{
    private readonly FaceLivenessApi _face;
    private readonly AttestationService _attestation;
    private readonly IAppSessionStore _appSessions;
    private readonly AppSettings _settings;

    public IndexModel(FaceLivenessApi face, AttestationService attestation, IAppSessionStore appSessions, IOptions<AppSettings> settings)
    {
        _face = face;
        _attestation = attestation;
        _appSessions = appSessions;
        _settings = settings.Value;
    }

    [BindProperty] public string Resource { get; set; } = "";
    [BindProperty] public string ApiKey { get; set; } = "";
    [BindProperty] public string Mode { get; set; } = "Passive";
    [BindProperty] public IFormFile? VerifyImage { get; set; }

    public string? Error { get; private set; }

    // Presence only — never expose secret values to the browser. Mirrors the
    // React sample's required/optional deep-link + attestation variables.
    public IReadOnlyList<ConfigVar> RequiredVars => new[]
    {
        new ConfigVar("IOS_APP_ID", NotBlank(_settings.IosAppId), "iOS App Attest: TeamID.BundleID the attestation/assertion must match."),
        new ConfigVar("IOS_APP_CLIP_ID", NotBlank(_settings.IosAppClipId), "iOS App Clip appID written into the AASA appclips section."),
        new ConfigVar("IOS_APPLINK_APP_ID", NotBlank(_settings.IosApplinkAppId), "Universal Link AASA appIDs binding the domain to the iOS app (falls back to IOS_APP_ID)."),
        new ConfigVar("ANDROID_PACKAGE_NAME", NotBlank(_settings.AndroidPackageName), "Android applicationId used for Play Integrity checks and the assetlinks binding."),
        new ConfigVar("ANDROID_SHA256_CERT_FINGERPRINTS", NotBlank(_settings.AndroidSha256CertFingerprints), "App Link assetlinks signing-cert SHA-256 fingerprints."),
        new ConfigVar("GOOGLE_SERVICE_ACCOUNT_JSON", NotBlank(_settings.GoogleServiceAccountJson), "Credentials to call the Play Integrity API (secret — value never shown)."),
        new ConfigVar("APPLINK_PATH", NotBlank(_settings.ApplinkPath), "Universal/App Link path pattern (defaults to /native* when unset)."),
    };

    public IReadOnlyList<ConfigVar> OptionalVars => new[]
    {
        new ConfigVar("DEBUG_MODE", _settings.DebugMode, "Relaxes dev attestation policy (App Attest develop aaguid; Android UNRECOGNIZED_VERSION / weaker integrity)."),
        new ConfigVar("ALLOW_DEVICE_INTEGRITY", _settings.AllowDeviceIntegrity, "Accept Play Integrity MEETS_DEVICE_INTEGRITY (not only STRONG)."),
        new ConfigVar("ALLOW_BASIC_INTEGRITY", _settings.AllowBasicIntegrity, "Accept Play Integrity MEETS_BASIC_INTEGRITY (not only STRONG)."),
        new ConfigVar("ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE", _settings.AllowAndroidAttestationWhenGoogleUnavailable, "Accept Android attestation on hardware Key Attestation alone when the Play Integrity API is unavailable / quota-exceeded."),
        new ConfigVar("IOS_APP_STORE_URL", NotBlank(_settings.IosAppStoreUrl), "App Store link on the landing page; also the iOS App Clip URL."),
        new ConfigVar("ANDROID_PLAY_STORE_URL", NotBlank(_settings.AndroidPlayStoreUrl), "Google Play link shown on the landing page."),
    };

    public IReadOnlyList<string> MissingRequired => RequiredVars.Where(v => !v.Set).Select(v => v.Name).ToList();

    public void OnGet() { }

    public async Task<IActionResult> OnPostAsync()
    {
        if (string.IsNullOrWhiteSpace(Resource) || string.IsNullOrWhiteSpace(ApiKey))
        {
            Error = "Face resource and API key are required.";
            return Page();
        }

        try
        {
            byte[]? image = null;
            var imageName = "verify.jpg";
            if (VerifyImage is { Length: > 0 })
            {
                using var ms = new MemoryStream();
                await VerifyImage.CopyToAsync(ms);
                image = ms.ToArray();
                imageName = VerifyImage.FileName;
            }

            var action = image is null ? "detectLiveness" : "detectLivenessWithVerify";
            var session = await _face.CreateSessionAsync(Resource.Trim(), ApiKey.Trim(), Mode, image, imageName);
            if (string.IsNullOrEmpty(session.SessionId) || string.IsNullOrEmpty(session.AuthToken))
            {
                Error = "Face service did not return a session.";
                return Page();
            }

            await _attestation.SaveSessionAsync(session.SessionId, session.AuthToken);
            await _appSessions.SaveAsync(session.SessionId, new AppSession
            {
                Resource = Resource.Trim(),
                ApiKey = ApiKey.Trim(),
                Action = action,
            });

            return Redirect($"/native?s={session.SessionId}");
        }
        catch (FaceApiException e)
        {
            Error = $"Face API error ({e.Status}): {e.Message}";
            return Page();
        }
    }

    private static bool NotBlank(string? value) => !string.IsNullOrWhiteSpace(value);
}

/// <summary>Presence status of one configuration variable shown on the index page.</summary>
public sealed record ConfigVar(string Name, bool Set, string Purpose);
