using Azure.AI.Vision.Face.DeviceAttestation.Logging;

namespace Azure.AI.Vision.Face.DeviceAttestation.Services;

/// <summary>
/// Per-route failure telemetry. Emits a structured "Api.Fail" event so failure
/// reasons can be grouped/queried by route + reason code.
/// </summary>
internal static class ApiTelemetry
{
    public static void TrackApiFail(
        IAttestationLogger logger,
        string route,
        string reason,
        int status,
        IReadOnlyDictionary<string, object?>? properties = null)
    {
        var props = new Dictionary<string, object?>
        {
            ["route"] = route,
            ["reason"] = reason,
            ["status"] = status,
        };
        if (properties is not null)
        {
            foreach (var kv in properties)
            {
                props[kv.Key] = kv.Value;
            }
        }
        logger.TrackEvent("Api.Fail", props);
    }
}
