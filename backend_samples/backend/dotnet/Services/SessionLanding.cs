using FaceLivenessAttestationBackendSample.Configuration;
using Microsoft.Extensions.Options;
using QRCoder;

namespace FaceLivenessAttestationBackendSample.Services;

/// <summary>
/// Landing-page helpers: platform detection, QR generation, and construction
/// of the native-app launch, browser callback, and store-fallback URLs.
/// </summary>
public sealed class SessionLanding
{
    private readonly AppSettings _settings;

    public SessionLanding(IOptions<AppSettings> settings) => _settings = settings.Value;

    /// <summary>Coarse platform detection from a User-Agent string.</summary>
    public static string DetectPlatform(string? userAgent)
    {
        var ua = userAgent?.ToLowerInvariant() ?? "";
        if (ua.Contains("android"))
        {
            return "android";
        }
        if (ua.Contains("iphone") || ua.Contains("ipad") || ua.Contains("ipod"))
        {
            return "ios";
        }
        // iPadOS Safari masquerades as desktop macOS but retains "Mobile" in
        // its User-Agent string.
        if (ua.Contains("macintosh") && ua.Contains("mobile"))
        {
            return "ios";
        }
        return "desktop";
    }

    /// <summary>
    /// Resolve the public origin for a request behind App Service's TLS proxy.
    /// App Links are HTTPS-only, so the scheme is deliberately pinned to HTTPS.
    /// </summary>
    public static string GetPublicOrigin(HttpRequest request)
    {
        var forwardedHost = FirstHeaderValue(request.Headers["X-Forwarded-Host"].ToString());
        var disguisedHost = FirstHeaderValue(request.Headers["Disguised-Host"].ToString());

        foreach (var authority in new[] { forwardedHost, disguisedHost, request.Host.Value ?? "" })
        {
            if (TryBuildOrigin(authority, out var origin))
            {
                return origin;
            }
        }

        throw new InvalidOperationException("The request does not contain a valid public host.");
    }

    /// <summary>
    /// Build all URLs needed by the session page. The QR and Android launch URL
    /// target <c>/native</c>, while <c>callbackUrl</c> targets <c>/result</c> so
    /// completion returns to a browser instead of re-launching the app.
    /// </summary>
    public LandingLinks BuildLinks(
        string publicOrigin,
        string sessionId,
        string platform,
        string sessionIdParam = "s")
    {
        var nativeUrl = BuildPageUrl(publicOrigin, "/native", sessionIdParam, sessionId);
        var resultUrl = BuildPageUrl(publicOrigin, "/result", sessionIdParam, sessionId);
        var qrUrl = AppendQuery(nativeUrl, ("callbackUrl", resultUrl));

        string? actionUrl = platform switch
        {
            "android" => BuildAndroidActionUrl(qrUrl),
            "ios" => BuildIosActionUrl(publicOrigin, sessionId, sessionIdParam, resultUrl),
            _ => null,
        };

        return new LandingLinks(nativeUrl, resultUrl, qrUrl, actionUrl);
    }

    /// <summary>
    /// Build the iOS smart-banner content used by the sibling backend samples.
    /// It is omitted unless an iOS application identifier and session exist.
    /// </summary>
    public string? BuildSmartBannerContent(string sessionId)
    {
        var iosAppId = string.IsNullOrWhiteSpace(_settings.IosApplinkAppId)
            ? _settings.IosAppId
            : _settings.IosApplinkAppId;
        return !string.IsNullOrWhiteSpace(sessionId) && iosAppId?.Contains('.') == true
            ? $"app-argument={sessionId}"
            : null;
    }

    /// <summary>Render <paramref name="url"/> as a PNG QR code data URI.</summary>
    public static string BuildQrDataUri(string url)
    {
        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(url, QRCodeGenerator.ECCLevel.M);
        var png = new PngByteQRCode(data);
        return "data:image/png;base64," + Convert.ToBase64String(png.GetGraphic(6));
    }

    private string BuildAndroidActionUrl(string landingUrl)
    {
        var packageName = _settings.AndroidPackageName.Trim();
        if (string.IsNullOrEmpty(packageName))
        {
            return landingUrl;
        }

        // Preserve the complete launch URL as the Play Install Referrer so a
        // first launch after installation can resume this exact session.
        var fallbackUrl = string.IsNullOrWhiteSpace(_settings.AndroidPlayStoreUrl)
            ? landingUrl
            : AppendQuery(_settings.AndroidPlayStoreUrl, ("referrer", landingUrl));

        return BuildAndroidIntentUrl(landingUrl, packageName, fallbackUrl);
    }

    private string? BuildIosActionUrl(
        string publicOrigin,
        string sessionId,
        string sessionIdParam,
        string resultUrl)
    {
        if (string.IsNullOrWhiteSpace(_settings.IosAppStoreUrl))
        {
            return null;
        }

        var backendHost = new Uri(publicOrigin).Host;
        return AppendQuery(
            _settings.IosAppStoreUrl,
            (sessionIdParam, sessionId),
            ("callbackUrl", resultUrl),
            ("domain", backendHost));
    }

    private static string BuildAndroidIntentUrl(string httpsUrl, string packageName, string fallbackUrl)
    {
        var url = new Uri(httpsUrl, UriKind.Absolute);
        var target = url.Authority + url.AbsolutePath + url.Query;
        return $"intent://{target}#Intent;scheme=https;package={packageName};" +
               $"S.browser_fallback_url={Uri.EscapeDataString(fallbackUrl)};end";
    }

    private static string BuildPageUrl(string publicOrigin, string path, string key, string value)
    {
        var builder = new UriBuilder(publicOrigin)
        {
            Scheme = Uri.UriSchemeHttps,
            Path = path,
            Query = "",
            Fragment = "",
        };
        return AppendQuery(builder.Uri.AbsoluteUri, (key, value));
    }

    private static string AppendQuery(string url, params (string Key, string Value)[] parameters)
    {
        var query = string.Join("&", parameters
            .Where(p => !string.IsNullOrEmpty(p.Value))
            .Select(p => $"{Uri.EscapeDataString(p.Key)}={Uri.EscapeDataString(p.Value)}"));
        if (string.IsNullOrEmpty(query))
        {
            return url;
        }
        return url + (url.Contains('?') ? "&" : "?") + query;
    }

    private static string FirstHeaderValue(string value)
        => value.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
            .FirstOrDefault() ?? "";

    private static bool TryBuildOrigin(string authority, out string origin)
    {
        origin = "";
        if (string.IsNullOrWhiteSpace(authority)
            || !Uri.TryCreate($"https://{authority}", UriKind.Absolute, out var uri)
            || !string.IsNullOrEmpty(uri.UserInfo)
            || uri.AbsolutePath != "/"
            || !string.IsNullOrEmpty(uri.Query)
            || !string.IsNullOrEmpty(uri.Fragment))
        {
            return false;
        }

        origin = uri.GetLeftPart(UriPartial.Authority);
        return true;
    }
}

/// <summary>Native launch and browser callback URLs for one liveness session.</summary>
public sealed record LandingLinks(
    string NativeUrl,
    string ResultUrl,
    string QrUrl,
    string? ActionUrl);
