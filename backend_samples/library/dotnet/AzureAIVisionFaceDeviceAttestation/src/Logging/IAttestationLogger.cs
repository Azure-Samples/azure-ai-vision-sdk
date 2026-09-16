namespace Azure.AI.Vision.Face.DeviceAttestation.Logging;

/// <summary>An outbound dependency call to record (e.g. Play Integrity / revocation HTTP).</summary>
public sealed class DependencyTelemetry
{
    /// <summary>Logical name of the dependency call.</summary>
    public required string Name { get; init; }

    /// <summary>Remote target (host, service name, …).</summary>
    public string? Target { get; init; }

    /// <summary>Command/data associated with the call.</summary>
    public string? Data { get; init; }

    /// <summary>How long the call took.</summary>
    public required TimeSpan Duration { get; init; }

    /// <summary>Whether the call succeeded.</summary>
    public required bool Success { get; init; }

    /// <summary>Result/status code, if any.</summary>
    public string? ResultCode { get; init; }

    /// <summary>Dependency type name (e.g. "HTTP", "Redis").</summary>
    public string? DependencyTypeName { get; init; }

    /// <summary>Optional structured properties.</summary>
    public IReadOnlyDictionary<string, object?>? Properties { get; init; }
}

/// <summary>
/// Telemetry sink the host provides so the library can emit telemetry without
/// depending on a concrete SDK. The host installs an implementation (e.g. App
/// Insights) when constructing the <see cref="AttestationService"/>; if none is
/// supplied a no-op logger is used so unconfigured/test runs stay silent.
/// </summary>
public interface IAttestationLogger
{
    /// <summary>Record a named custom event with optional properties + numeric measurements.</summary>
    void TrackEvent(
        string name,
        IReadOnlyDictionary<string, object?>? properties = null,
        IReadOnlyDictionary<string, double>? measurements = null);

    /// <summary>Record an exception with optional properties.</summary>
    void TrackException(Exception error, IReadOnlyDictionary<string, object?>? properties = null);

    /// <summary>Record an outbound dependency call.</summary>
    void TrackDependency(DependencyTelemetry dependency);
}

/// <summary>A logger that discards all telemetry. Used as the default sink.</summary>
public sealed class NullAttestationLogger : IAttestationLogger
{
    /// <summary>Shared instance.</summary>
    public static readonly NullAttestationLogger Instance = new();

    private NullAttestationLogger() { }

    /// <inheritdoc />
    public void TrackEvent(string name, IReadOnlyDictionary<string, object?>? properties = null, IReadOnlyDictionary<string, double>? measurements = null) { }

    /// <inheritdoc />
    public void TrackException(Exception error, IReadOnlyDictionary<string, object?>? properties = null) { }

    /// <inheritdoc />
    public void TrackDependency(DependencyTelemetry dependency) { }
}
