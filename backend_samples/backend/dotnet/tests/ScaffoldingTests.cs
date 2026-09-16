using System.Net;
using System.Collections.Concurrent;
using System.Text.Json;
using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;
using FaceLivenessAttestationBackendSample.Configuration;
using FaceLivenessAttestationBackendSample.Services;
using FaceLivenessAttestationBackendSample.Storage;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Xunit;

namespace FaceLivenessAttestationBackendSample.Tests;

/// <summary>
/// Boots the app in-memory (no Redis / App Insights configured) and exercises the
/// HTTP surface, confirming the endpoints are wired to the attestation service
/// and serialize the expected camelCase JSON.
/// </summary>
public class EndpointsTests : IClassFixture<WebApplicationFactory<Program>>
{
    private readonly WebApplicationFactory<Program> _factory;

    public EndpointsTests(WebApplicationFactory<Program> factory) => _factory = factory;

    [Fact]
    public async Task Healthz_ReturnsOk()
    {
        var response = await _factory.CreateClient().GetAsync("/healthz");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Contains("\"status\":\"ok\"", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task AssetLinks_ServesGeneratedDocument()
    {
        var response = await _factory.CreateClient().GetAsync("/.well-known/assetlinks.json");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Contains("delegate_permission/common.handle_all_urls", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task AppleAppSiteAssociation_ServesGeneratedDocument()
    {
        var response = await _factory.CreateClient().GetAsync("/.well-known/apple-app-site-association");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Contains("applinks", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task Challenge_UnknownSession_Returns404()
    {
        var response = await _factory.CreateClient()
            .PostAsync("/api/attestation/challenge?s=11111111-1111-1111-1111-111111111111&cid=com.x&sys=android", null);
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Contains("Session not found", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task Challenge_InvalidUuid_Returns400()
    {
        var response = await _factory.CreateClient()
            .PostAsync("/api/attestation/challenge?s=bad&cid=com.x&sys=android", null);
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public void AttestationLogger_FallsBackToHostLogging_WhenAppInsightsIsAbsent()
    {
        using var provider = new CapturingLoggerProvider();
        using var factory = _factory.WithWebHostBuilder(builder =>
        {
            builder.UseSetting("APPLICATIONINSIGHTS_CONNECTION_STRING", "");
            builder.ConfigureLogging(logging =>
            {
                logging.ClearProviders();
                logging.AddProvider(provider);
            });
        });
        var logger = factory.Services.GetRequiredService<IAttestationLogger>();

        logger.TrackEvent(
            "Test.Event",
            new Dictionary<string, object?> { ["publicKey"] = "PUBLIC_KEY" },
            new Dictionary<string, double> { ["durationMs"] = 12 });
        logger.TrackException(new InvalidOperationException("test failure"),
            new Dictionary<string, object?> { ["source"] = "test" });
        logger.TrackDependency(new DependencyTelemetry
        {
            Name = "Test.Dependency",
            Target = "example.test",
            Duration = TimeSpan.FromMilliseconds(7),
            Success = true,
            ResultCode = "200",
        });

        Assert.Contains(provider.Messages, message => message.Contains("Test.Event") && message.Contains("PUBLIC_KEY"));
        Assert.Contains(provider.Messages, message => message.Contains("test failure"));
        Assert.Contains(provider.Messages, message => message.Contains("Test.Dependency") && message.Contains("example.test"));
    }

    [Fact]
    public async Task Index_SurfacesResourceEndpointAndApplinkPath()
    {
        var html = await _factory.CreateClient().GetStringAsync("/");
        Assert.Contains("Face resource name", html);
        Assert.Contains("https://", html);
        Assert.Contains(".cognitiveservices.azure.com", html);
        Assert.Contains("APPLINK_PATH", html);
    }

    [Theory]
    [InlineData("Mozilla/5.0 (Linux; Android 15)", "android")]
    [InlineData("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0)", "ios")]
    [InlineData("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) Mobile/15E148", "ios")]
    [InlineData("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "desktop")]
    public void SessionLanding_DetectsPlatform(string userAgent, string expected)
        => Assert.Equal(expected, SessionLanding.DetectPlatform(userAgent));

    [Fact]
    public void SessionLanding_UsesForwardedPublicHostAndHttps()
    {
        var context = new DefaultHttpContext();
        context.Request.Host = new HostString("internal-host", 8080);
        context.Request.Headers["X-Forwarded-Host"] = "liveness.example.com, internal-host:8080";

        Assert.Equal("https://liveness.example.com", SessionLanding.GetPublicOrigin(context.Request));
    }

    [Fact]
    public void SessionLanding_AndroidQrAndIntentCarryResultCallback()
    {
        var landing = CreateLanding(new AppSettings
        {
            AndroidPackageName = "com.microsoft.liveness",
            AndroidPlayStoreUrl = "https://play.google.com/store/apps/details?id=com.microsoft.liveness",
        });

        var links = landing.BuildLinks(
            "https://liveness.example.com",
            "11111111-1111-1111-1111-111111111111",
            "android");

        Assert.Equal(
            "https://liveness.example.com/native?s=11111111-1111-1111-1111-111111111111",
            links.NativeUrl);
        Assert.Equal(
            "https://liveness.example.com/result?s=11111111-1111-1111-1111-111111111111",
            links.ResultUrl);
        Assert.Equal(
            links.NativeUrl + "&callbackUrl=" + Uri.EscapeDataString(links.ResultUrl),
            links.QrUrl);
        var actionUrl = Assert.IsType<string>(links.ActionUrl);
        Assert.StartsWith("intent://liveness.example.com/native?", actionUrl);
        Assert.Contains("#Intent;scheme=https;package=com.microsoft.liveness;", actionUrl);
        Assert.Contains("S.browser_fallback_url=", actionUrl);
        Assert.Contains(Uri.EscapeDataString("referrer="), actionUrl);
    }

    [Fact]
    public void SessionLanding_IosAppClipCarriesSessionCallbackAndBackendDomain()
    {
        var landing = CreateLanding(new AppSettings
        {
            IosAppId = "TEAMID.com.microsoft.liveness",
            IosAppStoreUrl = "https://apps.apple.com/app/id123456789",
        });

        var links = landing.BuildLinks(
            "https://liveness.example.com",
            "22222222-2222-2222-2222-222222222222",
            "ios");

        var actionUrl = Assert.IsType<string>(links.ActionUrl);
        Assert.Contains("s=22222222-2222-2222-2222-222222222222", actionUrl);
        Assert.Contains("callbackUrl=" + Uri.EscapeDataString(links.ResultUrl), actionUrl);
        Assert.Contains("domain=liveness.example.com", actionUrl);
        Assert.Equal(
            "app-argument=22222222-2222-2222-2222-222222222222",
            landing.BuildSmartBannerContent("22222222-2222-2222-2222-222222222222"));
    }

    [Fact]
    public async Task NativeSession_RendersDigestResultAndFlowGuideUi()
    {
        var sid = Guid.NewGuid().ToString();
        var attestation = _factory.Services.GetRequiredService<AttestationService>();
        Assert.Equal(sid, await attestation.SaveSessionAsync(sid, "test-token"));

        using var request = new HttpRequestMessage(HttpMethod.Get, $"/native?s={sid}");
        request.Headers.UserAgent.ParseAdd("Mozilla/5.0 (Linux; Android 15)");
        var response = await _factory.CreateClient().SendAsync(request);
        response.EnsureSuccessStatusCode();
        var html = await response.Content.ReadAsStringAsync();

        Assert.Contains("Client digest", html);
        Assert.Contains("Liveness service result (JSON)", html);
        Assert.Contains("How the API flow works", html);
        Assert.Contains("callbackUrl", html);
        Assert.Contains("window.setTimeout(poll, 1000)", html);
        Assert.Contains("DIGEST_MISMATCH", html);
        Assert.Contains("Session rejected: the client and service digests are missing or do not match.", html);
        Assert.Contains("response.ok && data.status === 'done'", html);
    }

    [Theory]
    [InlineData("client-digest", "client-digest", true, true, 200, "done")]
    [InlineData("client-digest", "other-digest", true, true, 409, "error")]
    [InlineData("client-digest", "CLIENT-DIGEST", true, true, 409, "error")]
    [InlineData("client-digest", null, true, true, 409, "error")]
    [InlineData(null, "service-digest", true, true, 409, "error")]
    [InlineData(null, null, true, true, 409, "error")]
    [InlineData("", "", true, true, 409, "error")]
    [InlineData(" ", " ", true, true, 409, "error")]
    [InlineData("123", 123, true, true, 409, "error")]
    [InlineData("client-digest", "other-digest", false, true, 200, "pending")]
    [InlineData("client-digest", null, true, false, 200, "pending")]
    public async Task SessionResult_RequiresMatchingDigests(string? clientDigest, object? serviceDigest,
        bool completed, bool hasDecision, int expectedStatus, string expectedState)
    {
        var upstreamResult = new JsonObject();
        if (hasDecision) upstreamResult["livenessDecision"] = "real";
        if (serviceDigest is not null) upstreamResult["digest"] = JsonSerializer.SerializeToNode(serviceDigest);
        var upstreamJson = new JsonObject
        {
            ["results"] = new JsonObject
            {
                ["attempts"] = new JsonArray(new JsonObject { ["result"] = upstreamResult }),
            },
        }.ToJsonString();
        using var handler = new ResultHttpHandler(upstreamJson);
        using var faceClient = new HttpClient(handler);
        using var factory = _factory.WithWebHostBuilder(builder => builder.ConfigureServices(services =>
            services.AddSingleton(new FaceLivenessApi(faceClient, Options.Create(new AppSettings())))));
        var sid = Guid.NewGuid().ToString();
        var store = factory.Services.GetRequiredService<IClusterStore>();
        Assert.True(await store.SetSessionAsync(sid, new SessionRecord
        {
            Token = "test-token",
            Data = new JsonObject { ["digestCompleted"] = completed, ["digest"] = clientDigest },
        }));
        Assert.True(await factory.Services.GetRequiredService<IAppSessionStore>().SaveAsync(sid, new AppSession
        {
            Resource = "test-resource", ApiKey = "test-key", Action = "detectLiveness",
        }));

        var response = await factory.CreateClient().GetAsync($"/api/session/result?s={sid}");
        Assert.Equal(expectedStatus, (int)response.StatusCode);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal(expectedState, body.RootElement.GetProperty("status").GetString());
        Assert.Equal(completed ? 1 : 0, handler.Calls);
        if (expectedState == "done")
        {
            Assert.Equal(clientDigest, body.RootElement.GetProperty("clientDigest").GetString());
            Assert.True(body.RootElement.TryGetProperty("result", out _));
        }
        else
        {
            Assert.False(body.RootElement.TryGetProperty("result", out _));
            Assert.False(body.RootElement.TryGetProperty("clientDigest", out _));
            if (expectedState == "error")
                Assert.Equal("DIGEST_MISMATCH", body.RootElement.GetProperty("code").GetString());
        }
    }

    private sealed class ResultHttpHandler(string result) : HttpMessageHandler
    {
        public int Calls { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(result) });
        }
    }

    private static SessionLanding CreateLanding(AppSettings settings)
        => new(Options.Create(settings));

    private sealed class CapturingLoggerProvider : ILoggerProvider
    {
        public ConcurrentQueue<string> Messages { get; } = new();

        public ILogger CreateLogger(string categoryName) => new CapturingLogger(Messages);

        public void Dispose() { }

        private sealed class CapturingLogger(ConcurrentQueue<string> messages) : ILogger
        {
            public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;

            public bool IsEnabled(LogLevel logLevel) => true;

            public void Log<TState>(LogLevel logLevel, EventId eventId, TState state,
                Exception? exception, Func<TState, Exception?, string> formatter)
                => messages.Enqueue(formatter(state, exception));
        }
    }
}

