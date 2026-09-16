using Azure.AI.Vision.Face.DeviceAttestation;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;
using FaceLivenessAttestationBackendSample.Configuration;
using FaceLivenessAttestationBackendSample.Endpoints;
using FaceLivenessAttestationBackendSample.Services;
using FaceLivenessAttestationBackendSample.Storage;
using Microsoft.ApplicationInsights;
using Microsoft.Extensions.Options;

var builder = WebApplication.CreateBuilder(args);

builder.Services.Configure<AppSettings>(builder.Configuration.GetSection("AppSettings"));
builder.Services.Configure<ApiRoutes>(builder.Configuration.GetSection("ApiRoutes"));

// Application Insights auto-collection (no-op if no connection string is set).
var appInsightsConnection = builder.Configuration["APPLICATIONINSIGHTS_CONNECTION_STRING"];
if (!string.IsNullOrEmpty(appInsightsConnection))
{
    builder.Services.AddApplicationInsightsTelemetry(options => options.ConnectionString = appInsightsConnection);
}

builder.Services.AddRazorPages();
builder.Services.AddHttpClient<FaceLivenessApi>();
builder.Services.AddSingleton<SessionLanding>();

var settings = builder.Configuration.GetSection("AppSettings").Get<AppSettings>() ?? new AppSettings();
var sessionTtl = TimeSpan.FromSeconds(settings.SessionTokenTtl);
var certTtl = TimeSpan.FromSeconds(settings.CertTtl);

// --- Pluggable storage ---------------------------------------------------------
// The attestation library persists through the IClusterStore interface, so the
// host owns storage. This sample uses Redis when available and falls back to an
// in-memory store; to use SQL/Cosmos/etc., implement IClusterStore and register
// it here instead — nothing else changes.
using var startupLoggerFactory = LoggerFactory.Create(b => b.AddConsole());
var startupLogger = startupLoggerFactory.CreateLogger("Startup");
var redis = await RedisConnection.CreateAsync(settings, startupLogger);
if (redis is not null)
{
    builder.Services.AddSingleton(redis);
    builder.Services.AddSingleton<IClusterStore>(_ => new RedisClusterStore(redis, sessionTtl, certTtl));
    builder.Services.AddSingleton<IAppSessionStore>(_ => new RedisAppSessionStore(redis, sessionTtl));
}
else
{
    startupLogger.LogWarning("Redis not configured/available — using in-memory stores (single-instance, non-persistent).");
    builder.Services.AddSingleton<IClusterStore>(new InMemoryClusterStore(sessionTtl, certTtl));
    builder.Services.AddSingleton<IAppSessionStore>(new InMemoryAppSessionStore(sessionTtl));
}

// The library's telemetry sink (App Insights, or host console logging when unconfigured).
builder.Services.AddSingleton<IAttestationLogger>(sp => new AppInsightsAttestationLogger(
    sp.GetService<TelemetryClient>(),
    sp.GetRequiredService<ILogger<AppInsightsAttestationLogger>>()));

// The attestation service singleton: config + store + logger injected once.
builder.Services.AddSingleton(sp => AttestationService.Create(
    sp.GetRequiredService<IOptions<AppSettings>>().Value.ToAttestationConfig(),
    sp.GetRequiredService<IClusterStore>(),
    sp.GetRequiredService<IAttestationLogger>()));

var app = builder.Build();

// Permissive CORS (native apps call /api/* from any origin) + security headers.
app.Use(async (context, next) =>
{
    var headers = context.Response.Headers;
    headers["Access-Control-Allow-Origin"] = "*";
    headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS";
    headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type";
    headers["X-Frame-Options"] = "DENY";
    headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains; preload";
    if (HttpMethods.IsOptions(context.Request.Method))
    {
        context.Response.StatusCode = StatusCodes.Status204NoContent;
        return;
    }
    await next();
});

app.UseExceptionHandler(errorApp => errorApp.Run(async context =>
{
    context.Response.StatusCode = StatusCodes.Status500InternalServerError;
    context.Response.ContentType = "application/json";
    await context.Response.WriteAsync("{\"message\":\"Internal server error\"}");
}));

app.UseStaticFiles();
app.MapAttestationApi();
app.MapRazorPages();

app.Run();

// Exposed so the integration-test project can boot the app via WebApplicationFactory.
public partial class Program { }
