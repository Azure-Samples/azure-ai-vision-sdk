using System.Diagnostics;
using System.Net.Http;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using System.Text.Json.Serialization;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;

namespace Azure.AI.Vision.Face.DeviceAttestation.Android;

/// <summary>A revocation list entry.</summary>
internal sealed class RevocationStatusEntry
{
    public string? Status { get; init; }
    public string? Reason { get; init; }
}

/// <summary>Google's Android Key Attestation revocation status list.</summary>
internal sealed class RevocationStatusList
{
    [JsonPropertyName("entries")]
    public Dictionary<string, RevocationStatusEntry>? Entries { get; init; }
}

/// <summary>
/// Google Android Key Attestation certificate revocation list, fetched lazily
/// and cached in-process. <see cref="CheckCertificateRevocation"/> fails closed:
/// if the list can't be fetched, every cert is treated as revoked.
/// https://android.googleapis.com/attestation/status
/// </summary>
internal static class Revocation
{
    private const string RevocationStatusUrl = "https://android.googleapis.com/attestation/status";

    private static readonly HttpClient Http = new();
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private static readonly object CacheLock = new();

    private static RevocationStatusList? _cache;
    private static DateTime _fetchedAt;
    private static TimeSpan _ttl = TimeSpan.FromHours(1);

    public static async Task<RevocationStatusList?> FetchRevocationStatusListAsync(IAttestationLogger logger)
    {
        lock (CacheLock)
        {
            if (_cache is not null && DateTime.UtcNow - _fetchedAt < _ttl)
            {
                return _cache;
            }
        }

        var sw = Stopwatch.StartNew();
        try
        {
            using var response = await Http.GetAsync(RevocationStatusUrl);
            sw.Stop();
            if (!response.IsSuccessStatusCode)
            {
                logger.TrackDependency(new DependencyTelemetry
                {
                    Name = "AndroidAttestation.RevocationList",
                    Target = "android.googleapis.com",
                    Data = RevocationStatusUrl,
                    Duration = sw.Elapsed,
                    Success = false,
                    ResultCode = ((int)response.StatusCode).ToString(),
                });
                return null;
            }

            if (response.Headers.CacheControl?.MaxAge is { } maxAge)
            {
                _ttl = maxAge;
            }

            var json = await response.Content.ReadAsStringAsync();
            var statusList = JsonSerializer.Deserialize<RevocationStatusList>(json, JsonOptions);

            lock (CacheLock)
            {
                _cache = statusList;
                _fetchedAt = DateTime.UtcNow;
            }

            logger.TrackDependency(new DependencyTelemetry
            {
                Name = "AndroidAttestation.RevocationList",
                Target = "android.googleapis.com",
                Data = RevocationStatusUrl,
                Duration = sw.Elapsed,
                Success = true,
                ResultCode = "200",
                Properties = new Dictionary<string, object?> { ["entryCount"] = statusList?.Entries?.Count ?? 0 },
            });
            return statusList;
        }
        catch (Exception e)
        {
            sw.Stop();
            logger.TrackDependency(new DependencyTelemetry
            {
                Name = "AndroidAttestation.RevocationList",
                Target = "android.googleapis.com",
                Data = RevocationStatusUrl,
                Duration = sw.Elapsed,
                Success = false,
                ResultCode = "exception",
            });
            logger.TrackException(e, new Dictionary<string, object?> { ["source"] = "fetchRevocationStatusList" });
            return null;
        }
    }

    public static (bool IsRevoked, string? Status, string? Reason) CheckCertificateRevocation(byte[] certDer, RevocationStatusList? statusList)
    {
        if (statusList?.Entries is null)
        {
            // Fail closed: an attestation we can't check against revocations is rejected.
            return (true, "REVOKED", "REVOCATION_CHECK_UNAVAILABLE");
        }

        try
        {
            using var cert = X509CertificateLoader.LoadCertificate(certDer);
            var serialHex = cert.SerialNumber.ToLowerInvariant().TrimStart('0');
            if (serialHex.Length == 0) serialHex = "0";
            if (statusList.Entries.TryGetValue(serialHex, out var entry))
            {
                return (true, entry.Status, entry.Reason ?? "UNSPECIFIED");
            }
            return (false, null, null);
        }
        catch
        {
            return (false, null, null);
        }
    }
}
