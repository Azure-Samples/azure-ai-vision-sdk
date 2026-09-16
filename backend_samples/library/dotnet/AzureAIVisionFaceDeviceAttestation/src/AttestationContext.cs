using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;

namespace Azure.AI.Vision.Face.DeviceAttestation;

/// <summary>
/// The injected dependencies threaded through the handlers and platform
/// verifiers: runtime config, the persistent store, and the telemetry sink.
/// Replaces the npm library's mutable module-global config/logger with explicit
/// dependency injection.
/// </summary>
internal sealed class AttestationContext
{
    public required AttestationConfig Config { get; init; }
    public required IClusterStore Store { get; init; }
    public required IAttestationLogger Logger { get; init; }
}
