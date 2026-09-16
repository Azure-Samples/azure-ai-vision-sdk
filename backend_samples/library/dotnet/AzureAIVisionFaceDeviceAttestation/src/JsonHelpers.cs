using System.Text.Json.Nodes;

namespace Azure.AI.Vision.Face.DeviceAttestation;

/// <summary>Small readers over the session-state <see cref="JsonObject"/>.</summary>
internal static class JsonHelpers
{
    /// <summary>Non-empty string value, or null.</summary>
    public static string? GetString(JsonObject obj, string key)
        => obj.TryGetPropertyValue(key, out var node) && node is JsonValue value && value.TryGetValue<string>(out var s)
            ? s
            : null;

    /// <summary>Boolean value (true only when the node is JSON true).</summary>
    public static bool GetBool(JsonObject obj, string key)
        => obj.TryGetPropertyValue(key, out var node) && node is JsonValue value && value.TryGetValue<bool>(out var b) && b;

    /// <summary>Integral value (accepts long/int/double), or null.</summary>
    public static long? GetLong(JsonObject obj, string key)
    {
        if (obj.TryGetPropertyValue(key, out var node) && node is JsonValue value)
        {
            if (value.TryGetValue<long>(out var l))
            {
                return l;
            }
            if (value.TryGetValue<int>(out var i))
            {
                return i;
            }
            if (value.TryGetValue<double>(out var d))
            {
                return (long)d;
            }
        }
        return null;
    }
}
