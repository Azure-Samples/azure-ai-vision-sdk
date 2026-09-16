using System.Collections.Concurrent;
using System.Text.Json;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;

namespace FaceLivenessAttestationBackendSample.Storage;

/// <summary>
/// In-memory <see cref="IClusterStore"/> used when Redis is not configured or
/// unavailable, so the sample runs out-of-the-box for demos. Serializes values
/// (like the Redis store) so behavior matches, and honors TTL.
/// </summary>
internal sealed class InMemoryClusterStore : IClusterStore
{
    private sealed record Entry(string Json, DateTimeOffset Expiry, string Version);

    private readonly ConcurrentDictionary<string, Entry> _sessions = new();
    private readonly ConcurrentDictionary<string, Entry> _certs = new();
    private readonly TimeSpan _sessionTtl;
    private readonly TimeSpan _certTtl;

    public InMemoryClusterStore(TimeSpan sessionTtl, TimeSpan certTtl)
    {
        _sessionTtl = sessionTtl;
        _certTtl = certTtl;
    }

    public Task<Snapshot<SessionRecord>?> GetSessionAsync(string sid) => Task.FromResult(Get<SessionRecord>(_sessions, sid));
    public Task<bool> SetSessionAsync(string sid, SessionRecord record) => Task.FromResult(Set(_sessions, sid, record, _sessionTtl));
    public Task<UpdateResult> UpdateSessionAsync(string sid, string expectedVersion, SessionRecord record) => Task.FromResult(Update(_sessions, sid, expectedVersion, record));

    public Task<Snapshot<CertificateData>?> GetCertificateAsync(string thumbprint) => Task.FromResult(Get<CertificateData>(_certs, thumbprint));
    public Task<bool> SetCertificateAsync(string thumbprint, CertificateData data) => Task.FromResult(Set(_certs, thumbprint, data, _certTtl));
    public Task<UpdateResult> UpdateCertificateAsync(string thumbprint, string expectedVersion, CertificateData data) => Task.FromResult(Update(_certs, thumbprint, expectedVersion, data));

    private static Snapshot<T>? Get<T>(ConcurrentDictionary<string, Entry> store, string key) where T : class
    {
        lock (store)
        {
        if (store.TryGetValue(key, out var entry))
        {
            if (entry.Expiry > DateTimeOffset.UtcNow)
            {
                return new Snapshot<T>(JsonSerializer.Deserialize<T>(entry.Json, StoreSerialization.Options)!, entry.Version);
            }
            store.TryRemove(key, out _);
        }
        return null;
        }
    }

    private static bool Set<T>(ConcurrentDictionary<string, Entry> store, string key, T value, TimeSpan ttl)
    {
        lock (store)
        {
            if (ttl <= TimeSpan.Zero) throw new StorageException("Invalid attestation TTL");
            if (store.TryGetValue(key, out var current) && current.Expiry > DateTimeOffset.UtcNow) return false;
            store[key] = new Entry(JsonSerializer.Serialize(value, StoreSerialization.Options), DateTimeOffset.UtcNow + ttl, Guid.NewGuid().ToString("N"));
            return true;
        }
    }

    private static UpdateResult Update<T>(ConcurrentDictionary<string, Entry> store, string key, string expectedVersion, T value)
    {
        lock (store)
        {
        if (!store.TryGetValue(key, out var existing) || existing.Expiry <= DateTimeOffset.UtcNow)
        {
            return UpdateResult.MissingOrExpired;
        }
        if (existing.Version != expectedVersion) return UpdateResult.Conflict;
        store[key] = existing with { Json = JsonSerializer.Serialize(value, StoreSerialization.Options), Version = Guid.NewGuid().ToString("N") };
        return UpdateResult.Applied;
        }
    }
}

/// <summary>In-memory <see cref="IAppSessionStore"/> fallback.</summary>
internal sealed class InMemoryAppSessionStore : IAppSessionStore
{
    private readonly ConcurrentDictionary<string, (AppSession Session, DateTimeOffset Expiry)> _store = new();
    private readonly TimeSpan _ttl;

    public InMemoryAppSessionStore(TimeSpan ttl) => _ttl = ttl;

    public Task<bool> SaveAsync(string sid, AppSession session)
    {
        _store[sid] = (session, DateTimeOffset.UtcNow + _ttl);
        return Task.FromResult(true);
    }

    public Task<AppSession?> GetAsync(string sid)
    {
        if (_store.TryGetValue(sid, out var entry) && entry.Expiry > DateTimeOffset.UtcNow)
        {
            return Task.FromResult<AppSession?>(entry.Session);
        }
        return Task.FromResult<AppSession?>(null);
    }
}
