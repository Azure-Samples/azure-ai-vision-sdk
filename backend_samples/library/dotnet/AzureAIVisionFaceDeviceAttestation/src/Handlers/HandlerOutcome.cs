namespace Azure.AI.Vision.Face.DeviceAttestation.Handlers;

/// <summary>
/// Typed result of every attestation route method. Splits the CLIENT-facing
/// suggestion (<see cref="Status"/> + <see cref="Body"/>) from HOST-facing
/// metadata (<see cref="Ok"/>, <see cref="Code"/>, <see cref="Message"/>,
/// <see cref="Data"/>) the host uses to decide what to return.
/// <para>
/// The host typically serializes <see cref="Body"/> with the suggested
/// <see cref="Status"/>, but may inspect <see cref="Ok"/>/<see cref="Code"/> to
/// substitute a message, hide 5xx internals, or act on <see cref="Data"/>
/// (e.g. the client digest).
/// </para>
/// </summary>
public sealed class HandlerOutcome
{
    /// <summary>True on success (2xx). Lets the host branch without parsing <see cref="Status"/>.</summary>
    public required bool Ok { get; init; }

    /// <summary>Stable machine-readable code: "OK" on success, else the failure reason.</summary>
    public required string Code { get; init; }

    /// <summary>Suggested HTTP status; the host MAY override it.</summary>
    public required int Status { get; init; }

    /// <summary>
    /// Suggested client-facing JSON body (a success payload or an
    /// <see cref="ErrorBody"/>). Declared as <see cref="object"/> so the host's
    /// serializer emits the runtime type's fields.
    /// </summary>
    public required object Body { get; init; }

    /// <summary>Human-readable detail for host logging/decisions; not required to be shown.</summary>
    public string? Message { get; init; }

    /// <summary>
    /// Endpoint-specific host-facing result (e.g. the register verdict or the
    /// digest's client digest). Null for endpoints without host-facing data.
    /// </summary>
    public object? Data { get; init; }
}

/// <summary>
/// Standard error response body. <see cref="ExpiredAt"/> / <see cref="ValidFrom"/>
/// accompany the certificate-time failures in the register/verify handlers.
/// </summary>
public sealed class ErrorBody
{
    /// <summary>Human-readable error message.</summary>
    public required string Message { get; init; }

    /// <summary>ISO timestamp when a certificate expired (cert-expiry failures).</summary>
    public string? ExpiredAt { get; init; }

    /// <summary>ISO timestamp from which a certificate becomes valid (not-yet-valid failures).</summary>
    public string? ValidFrom { get; init; }
}
