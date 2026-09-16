using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Services;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;

namespace Azure.AI.Vision.Face.DeviceAttestation;

/// <summary>
/// Liveness-completion signal for a session: whether the client has posted its
/// digest yet and, if so, the digest it submitted.
/// </summary>
public sealed class LivenessOutcome
{
    /// <summary>Whether the client has posted its liveness digest.</summary>
    public bool Completed { get; init; }

    /// <summary>The digest the client submitted, if completed.</summary>
    public string? ClientDigest { get; init; }
}

/// <summary>
/// Handles every attestation endpoint plus the two /.well-known documents. Build
/// it once at startup with <see cref="Create"/> (cache it as a singleton); the
/// host's routes call its methods, so the routes never wire up storage or config
/// and the library never touches the environment.
/// </summary>
public sealed partial class AttestationService
{
    private readonly AttestationContext _ctx;

    internal AttestationService(AttestationContext ctx) => _ctx = ctx;

    /// <summary>
    /// Configure the library and construct the service. Call once at startup and
    /// cache the result as a singleton.
    /// </summary>
    /// <param name="config">Runtime configuration (app IDs, cert limits, …).</param>
    /// <param name="store">Persistent session/certificate store implementation.</param>
    /// <param name="logger">Telemetry sink; defaults to a no-op logger.</param>
    public static AttestationService Create(
        AttestationConfig config,
        IClusterStore store,
        IAttestationLogger? logger = null)
        => new(new AttestationContext
        {
            Config = config,
            Store = store,
            Logger = logger ?? NullAttestationLogger.Instance,
        });

    /// <summary>Whether a session record exists (created by <see cref="SaveSessionAsync"/>), by id.</summary>
    public async Task<bool> SessionExistsAsync(string sid)
        => await ServerUtils.GetSessionDataAsync(_ctx, sid) is not null;

    /// <summary>
    /// Liveness-completion signal for a session: whether the client has posted
    /// its digest yet and, if so, the digest it submitted. Lets the host poll
    /// for completion without reading the library's internal session shape.
    /// </summary>
    public async Task<LivenessOutcome> GetLivenessOutcomeAsync(string sid)
    {
        var session = await ServerUtils.GetSessionDataAsync(_ctx, sid);
        if (session is null || !GetBool(session.Data, "digestCompleted"))
        {
            return new LivenessOutcome { Completed = false };
        }
        return new LivenessOutcome
        {
            Completed = true,
            ClientDigest = GetString(session.Data, "digest"),
        };
    }

    /// <summary>Seed a new session with the Face token; attestation state starts empty.</summary>
    public Task<string?> SaveSessionAsync(string sid, string token)
        => ServerUtils.SaveTokenAsync(_ctx, sid, token);

    /// <summary>GET /.well-known/apple-app-site-association</summary>
    public JsonObject AppleAppSiteAssociation() => WellKnown.AppleAppSiteAssociation(_ctx.Config);

    /// <summary>GET /.well-known/assetlinks.json</summary>
    public JsonArray AndroidAssetLinks() => WellKnown.AssetLinks(_ctx.Config);

    private static bool GetBool(JsonObject data, string key)
        => data.TryGetPropertyValue(key, out var node)
           && node is JsonValue value
           && value.TryGetValue<bool>(out var b)
           && b;

    private static string? GetString(JsonObject data, string key)
        => data.TryGetPropertyValue(key, out var node)
           && node is JsonValue value
           && value.TryGetValue<string>(out var s)
            ? s
            : null;
}
