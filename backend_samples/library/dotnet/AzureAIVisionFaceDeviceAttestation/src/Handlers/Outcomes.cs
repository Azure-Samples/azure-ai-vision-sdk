using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Services;

namespace Azure.AI.Vision.Face.DeviceAttestation.Handlers;

/// <summary>
/// Builds <see cref="HandlerOutcome"/> values. <see cref="Fail"/> emits the
/// failure telemetry in the same step so the response code and telemetry code
/// can never drift.
/// </summary>
internal static class Outcomes
{
    /// <summary>Build a success (or otherwise non-failure) outcome.</summary>
    public static HandlerOutcome Ok(object body, int status = 200, string? code = null, object? data = null)
    {
        bool ok = status < 400;
        return new HandlerOutcome
        {
            Ok = ok,
            Code = code ?? (ok ? "OK" : "ERROR"),
            Status = status,
            Body = body,
            Message = body is ErrorBody e ? e.Message : null,
            Data = data,
        };
    }

    /// <summary>Build a FAILURE outcome AND emit its failure telemetry in one step.</summary>
    public static HandlerOutcome Fail(
        IAttestationLogger logger,
        string route,
        int status,
        string code,
        string message,
        IReadOnlyDictionary<string, object?>? properties = null,
        string? expiredAt = null,
        string? validFrom = null)
    {
        ApiTelemetry.TrackApiFail(logger, route, code, status, properties);
        return new HandlerOutcome
        {
            Ok = false,
            Code = code,
            Status = status,
            Message = message,
            Body = new ErrorBody { Message = message, ExpiredAt = expiredAt, ValidFrom = validFrom },
        };
    }
}
