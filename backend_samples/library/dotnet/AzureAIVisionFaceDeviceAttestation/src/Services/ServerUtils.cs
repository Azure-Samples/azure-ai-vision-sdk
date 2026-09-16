using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;

namespace Azure.AI.Vision.Face.DeviceAttestation.Services;

/// <summary>Read record for a loaded session: token, mutable state, and id.</summary>
internal sealed record SessionData(string Token, JsonObject Data, string Sid, string Version);

/// <summary>
/// Session-domain storage helpers. Every function receives the
/// <see cref="AttestationContext"/> and delegates ALL persistence (connection,
/// key schema, serialization, TTL) to the injected <see cref="IClusterStore"/>;
/// this layer keeps only the session-domain concerns (UUID validation, events).
/// </summary>
internal static class ServerUtils
{
    public static bool IsValidSessionId(string? sid)
        => sid is { Length: 36 } && Guid.TryParseExact(sid, "D", out _);

    /// <summary>Seed a session with its token (empty state; fresh TTL owned by the store).</summary>
    public static async Task<string?> SaveTokenAsync(AttestationContext ctx, string sid, string token)
    {
        var stored = await ctx.Store.SetSessionAsync(sid, new SessionRecord { Token = token, Data = new JsonObject() });
        return stored ? sid : null;
    }

    /// <summary>Read the {token, data, sid} record, or null if invalid/absent/expired.</summary>
    public static async Task<SessionData?> GetSessionDataAsync(AttestationContext ctx, string sid)
    {
        if (!IsValidSessionId(sid))
        {
            ctx.Logger.TrackEvent("SessionStore.GetSessionFail", new Dictionary<string, object?>
            {
                ["reason"] = "INVALID_UUID",
                ["sid"] = sid,
            });
            return null;
        }

        var record = await ctx.Store.GetSessionAsync(sid);
        if (record is null)
        {
            return null;
        }
        return new SessionData(record.Value.Token, record.Value.Data, sid, record.Version);
    }

    /// <summary>Write back {token, data}, preserving the existing TTL.</summary>
    public static async Task<bool> UpdateSessionDataAsync(AttestationContext ctx, string sid, string token, JsonObject data, string expectedVersion)
    {
        if (!IsValidSessionId(sid))
        {
            ctx.Logger.TrackEvent("SessionStore.UpdateFail", new Dictionary<string, object?>
            {
                ["reason"] = "INVALID_UUID",
                ["sid"] = sid,
            });
            return false;
        }
        return await ctx.Store.UpdateSessionAsync(sid, expectedVersion, new SessionRecord { Token = token, Data = data }) == UpdateResult.Applied;
    }
}
