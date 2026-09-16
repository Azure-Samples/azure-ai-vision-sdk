using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;

namespace Azure.AI.Vision.Face.DeviceAttestation.Handlers;

/// <summary>
/// Base for the attestation route handlers: exposes the injected context and the
/// shared <see cref="Fail"/> / <see cref="Ok"/> builders.
/// </summary>
internal abstract class HandlerBase
{
    protected AttestationContext Ctx { get; }
    protected AttestationConfig Config => Ctx.Config;
    protected IClusterStore Store => Ctx.Store;
    protected IAttestationLogger Logger => Ctx.Logger;

    protected HandlerBase(AttestationContext ctx) => Ctx = ctx;

    protected HandlerOutcome Fail(
        string route,
        int status,
        string code,
        string message,
        IReadOnlyDictionary<string, object?>? properties = null,
        string? expiredAt = null,
        string? validFrom = null)
        => Outcomes.Fail(Logger, route, status, code, message, properties, expiredAt, validFrom);

    protected static HandlerOutcome Ok(object body, int status = 200, string? code = null, object? data = null)
        => Outcomes.Ok(body, status, code, data);

    protected static Dictionary<string, object?> Props(string sid) => new() { ["sid"] = sid };
}
