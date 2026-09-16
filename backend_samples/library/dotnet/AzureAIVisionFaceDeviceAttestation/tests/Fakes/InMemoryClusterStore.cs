using System.Collections.Concurrent;
using System.Text.Json;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests.Fakes;

/// <summary>
/// In-memory <see cref="IClusterStore"/> for unit tests. Stores records by
/// detached snapshot (no TTL); <c>Update*</c> fails when the key is absent, matching the
/// contract that updates never resurrect a missing entry.
/// </summary>
internal sealed class InMemoryClusterStore : IClusterStore
{
    private readonly ConcurrentDictionary<string, Snapshot<SessionRecord>> _sessions = new();
    private readonly ConcurrentDictionary<string, Snapshot<CertificateData>> _certs = new();

    public Func<Task>? AfterSessionRead { get; set; }
    public bool FailWrites { get; set; }

    public async Task<Snapshot<SessionRecord>?> GetSessionAsync(string sid)
    {
        var snapshot = _sessions.TryGetValue(sid, out var record) ? Clone(record) : null;
        if (AfterSessionRead is not null) await AfterSessionRead();
        return snapshot;
    }

    public Task<bool> SetSessionAsync(string sid, SessionRecord record)
    {
        return Task.FromResult(_sessions.TryAdd(sid, new Snapshot<SessionRecord>(Clone(record), Guid.NewGuid().ToString())));
    }

    public Task<UpdateResult> UpdateSessionAsync(string sid, string expectedVersion, SessionRecord record)
    {
        if (FailWrites) throw new StorageException("Injected storage failure");
        return Task.FromResult(Update(_sessions, sid, expectedVersion, record));
    }

    public Task<Snapshot<CertificateData>?> GetCertificateAsync(string thumbprint)
        => Task.FromResult(_certs.TryGetValue(thumbprint, out var record) ? Clone(record) : null);

    public Task<bool> SetCertificateAsync(string thumbprint, CertificateData data)
    {
        return Task.FromResult(_certs.TryAdd(thumbprint, new Snapshot<CertificateData>(Clone(data), Guid.NewGuid().ToString())));
    }

    public Task<UpdateResult> UpdateCertificateAsync(string thumbprint, string expectedVersion, CertificateData data)
    {
        return Task.FromResult(Update(_certs, thumbprint, expectedVersion, data));
    }

    private static T Clone<T>(T value) => JsonSerializer.Deserialize<T>(JsonSerializer.Serialize(value))!;

    private static UpdateResult Update<T>(ConcurrentDictionary<string, Snapshot<T>> records, string key, string version, T value)
    {
        if (!records.TryGetValue(key, out var current)) return UpdateResult.MissingOrExpired;
        if (current.Version != version) return UpdateResult.Conflict;
        return records.TryUpdate(key, new Snapshot<T>(Clone(value), Guid.NewGuid().ToString()), current)
            ? UpdateResult.Applied : UpdateResult.Conflict;
    }
}
