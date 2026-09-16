using System.Text.Json;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;
using StackExchange.Redis;

namespace FaceLivenessAttestationBackendSample.Storage;

/// <summary>
/// Redis-backed <see cref="IClusterStore"/>. Sessions are keyed
/// in a versioned namespace. Creation uses NX; updates atomically compare the
/// snapshot version and preserve expiry using single-key Lua.
/// </summary>
internal sealed class RedisClusterStore : IClusterStore
{
    private readonly IDatabase _db;
    private readonly TimeSpan _sessionTtl;
    private readonly TimeSpan _certTtl;

    public RedisClusterStore(IConnectionMultiplexer connection, TimeSpan sessionTtl, TimeSpan certTtl)
    {
        _db = connection.GetDatabase();
        _sessionTtl = sessionTtl;
        _certTtl = certTtl;
    }

    private const string CasScript = """
        local current = redis.call('GET', KEYS[1])
        if not current or redis.call('PTTL', KEYS[1]) <= 0 then return 2 end
        if cjson.decode(current).version ~= ARGV[1] then return 1 end
        redis.call('SET', KEYS[1], ARGV[2], 'XX', 'KEEPTTL')
        return 0
        """;

    private static string SessionKey(string sid) => $"attestation:v2/{sid}";
    private static string CertKey(string thumbprint) => $"cert_thumb:v2:{thumbprint}";

    public Task<Snapshot<SessionRecord>?> GetSessionAsync(string sid) => GetAsync<SessionRecord>(SessionKey(sid));
    public Task<bool> SetSessionAsync(string sid, SessionRecord record) => SetAsync(SessionKey(sid), record, _sessionTtl);
    public Task<UpdateResult> UpdateSessionAsync(string sid, string expectedVersion, SessionRecord record) => UpdateAsync(SessionKey(sid), expectedVersion, record);

    public Task<Snapshot<CertificateData>?> GetCertificateAsync(string thumbprint) => GetAsync<CertificateData>(CertKey(thumbprint));
    public Task<bool> SetCertificateAsync(string thumbprint, CertificateData data) => SetAsync(CertKey(thumbprint), data, _certTtl);
    public Task<UpdateResult> UpdateCertificateAsync(string thumbprint, string expectedVersion, CertificateData data) => UpdateAsync(CertKey(thumbprint), expectedVersion, data);

    private Task<Snapshot<T>?> GetAsync<T>(string key) where T : class
        => ExecuteAsync<Snapshot<T>?>(async () =>
    {
        var value = await _db.StringGetAsync(key);
        if (value.IsNull) return null;
        var snapshot = JsonSerializer.Deserialize<Snapshot<T>>((string)value!, StoreSerialization.Options);
        if (snapshot?.Value is null || string.IsNullOrEmpty(snapshot.Version)) throw new StorageException("Invalid versioned attestation record");
        return snapshot;
    });

    private Task<bool> SetAsync<T>(string key, T value, TimeSpan ttl)
        => ExecuteAsync(async () =>
        {
            if (ttl <= TimeSpan.Zero) throw new StorageException("Invalid attestation TTL");
            return await _db.StringSetAsync(key, Serialize(value), ttl, when: When.NotExists);
        });

    private Task<UpdateResult> UpdateAsync<T>(string key, string expectedVersion, T value)
        => ExecuteAsync(async () =>
        {
            var result = (int)await _db.ScriptEvaluateAsync(CasScript, [key], [expectedVersion, Serialize(value)]);
            return result switch
            {
                0 => UpdateResult.Applied,
                1 => UpdateResult.Conflict,
                2 => UpdateResult.MissingOrExpired,
                _ => throw new StorageException("Unexpected CAS result"),
            };
        });

    private static string Serialize<T>(T value)
        => JsonSerializer.Serialize(new Snapshot<T>(value, Guid.NewGuid().ToString("N")), StoreSerialization.Options);

    private static async Task<T> ExecuteAsync<T>(Func<Task<T>> action)
    {
        try { return await action(); }
        catch (Exception error) { throw new StorageException("Attestation storage unavailable", error); }
    }
}

/// <summary>Redis-backed <see cref="IAppSessionStore"/> (key <c>session/{sid}</c>).</summary>
internal sealed class RedisAppSessionStore : IAppSessionStore
{
    private readonly IDatabase _db;
    private readonly TimeSpan _ttl;

    public RedisAppSessionStore(IConnectionMultiplexer connection, TimeSpan ttl)
    {
        _db = connection.GetDatabase();
        _ttl = ttl;
    }

    public Task<bool> SaveAsync(string sid, AppSession session)
        => _db.StringSetAsync($"session/{sid}", JsonSerializer.Serialize(session, StoreSerialization.Options), _ttl);

    public async Task<AppSession?> GetAsync(string sid)
    {
        var value = await _db.StringGetAsync($"session/{sid}");
        return value.IsNullOrEmpty ? null : JsonSerializer.Deserialize<AppSession>((string)value!, StoreSerialization.Options);
    }
}
