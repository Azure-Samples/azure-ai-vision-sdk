using System.Text.Json.Nodes;

namespace Azure.AI.Vision.Face.DeviceAttestation.Storage;

/// <summary>A detached record and its opaque persistence revision.</summary>
/// <param name="Value">Detached record value.</param>
/// <param name="Version">Opaque revision assigned by the store.</param>
public sealed record Snapshot<T>(T Value, string Version);

/// <summary>Result of an atomic conditional update.</summary>
public enum UpdateResult
{
    /// <summary>The replacement was committed.</summary>
    Applied,
    /// <summary>The persisted revision changed.</summary>
    Conflict,
    /// <summary>The record is missing or expired.</summary>
    MissingOrExpired,
}

/// <summary>A storage operation failed or its commit outcome is unknown.</summary>
public sealed class StorageException : Exception
{
    /// <summary>Construct a backend-neutral storage failure.</summary>
    public StorageException(string message, Exception? innerException = null) : base(message, innerException) { }
}

/// <summary>
/// Value stored under a session UUID: the Face session token plus the session
/// state object. The store owns how this record is serialized on the wire —
/// callers only ever see this structured shape, never a storage format.
/// </summary>
public sealed class SessionRecord
{
    /// <summary>The Face session token seeded when the session is created.</summary>
    public required string Token { get; set; }

    /// <summary>
    /// Mutable attestation session state (challenge hash, exchanged keys, cert
    /// thumbprint, and the completion flags). Modeled as a JSON object so the
    /// store can persist it verbatim.
    /// </summary>
    public required JsonObject Data { get; set; }
}

/// <summary>
/// Certificate record keyed by the SHA-256 thumbprint of the certificate DER.
/// The metadata blob holds the platform-specific verdict (PlayIntegrityVerdict /
/// AppAttestVerdict) plus the assertion sign-count bookkeeping.
/// </summary>
public sealed class CertificateData
{
    /// <summary>Client application ID that registered the certificate.</summary>
    public required string ClientId { get; set; }

    /// <summary>Platform: "ios" or "android".</summary>
    public required string System { get; set; }

    /// <summary>SHA-256 thumbprint of the certificate DER (64 hex chars).</summary>
    public required string Thumbprint { get; set; }

    /// <summary>Certificate in PEM format.</summary>
    public required string PublicCert { get; set; }

    /// <summary>ISO 8601 creation timestamp.</summary>
    public required string CreatedAt { get; set; }

    /// <summary>ISO 8601 last-verified timestamp.</summary>
    public required string LastVerifiedAt { get; set; }

    /// <summary>Platform verdict + per-call assertion bookkeeping.</summary>
    public JsonObject? Metadata { get; set; }
}

/// <summary>
/// Cluster-wide persistent-storage abstraction for the attestation flow. Two
/// entry kinds are stored: liveness <b>sessions</b> (keyed by session UUID) and
/// attestation <b>certificates</b> (keyed by SHA-256 thumbprint).
/// <para>
/// The store owns ALL time-to-live policy: <c>Set*</c> creates an absent
/// entry with the store's configured default TTL (session vs certificate);
/// <c>Update*</c> rewrites an existing entry while PRESERVING its remaining TTL
/// conditional on its snapshot version. Reads return detached snapshots; every
/// successful write assigns a unique version, including recreation. Failures
/// throw StorageException, never masquerade as missing records. Callers never pass TTLs.
/// </para>
/// <para>
/// The concrete implementation is provided by the host (see the ASP.NET Core
/// sample's Redis store) so a consumer can back the store with Redis, SQL, etc.
/// </para>
/// </summary>
public interface IClusterStore
{
    /// <summary>Read a session record by UUID, or null if absent/expired.</summary>
    Task<Snapshot<SessionRecord>?> GetSessionAsync(string sid);

    /// <summary>Create an absent session with a fresh TTL; false means already exists.</summary>
    Task<bool> SetSessionAsync(string sid, SessionRecord record);

    /// <summary>
    /// Atomically replace an existing session matching the version, preserving expiry.
    /// </summary>
    Task<UpdateResult> UpdateSessionAsync(string sid, string expectedVersion, SessionRecord record);

    /// <summary>Read a certificate record by thumbprint, or null if absent/expired.</summary>
    Task<Snapshot<CertificateData>?> GetCertificateAsync(string thumbprint);

    /// <summary>Create an absent certificate with a fresh TTL; false means already exists.</summary>
    Task<bool> SetCertificateAsync(string thumbprint, CertificateData data);

    /// <summary>
    /// Atomically replace an existing certificate matching the version, preserving expiry.
    /// </summary>
    Task<UpdateResult> UpdateCertificateAsync(string thumbprint, string expectedVersion, CertificateData data);
}
