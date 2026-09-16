using Azure.Identity;
using FaceLivenessAttestationBackendSample.Configuration;
using Microsoft.Azure.StackExchangeRedis;
using StackExchange.Redis;

namespace FaceLivenessAttestationBackendSample.Storage;

/// <summary>
/// Builds the Redis connection: a plain localhost connection in dev
/// (<c>UseLocalRedis</c>), or an Azure Managed Redis connection authenticated
/// with a Microsoft Entra token in production. Returns null if Redis is not
/// configured or the connection fails, so the host can fall back to in-memory.
/// </summary>
internal static class RedisConnection
{
    public static async Task<IConnectionMultiplexer?> CreateAsync(AppSettings settings, ILogger logger)
    {
        try
        {
            if (settings.UseLocalRedis)
            {
                logger.LogInformation("Connecting to local Redis at 127.0.0.1:6379");
                return await ConnectionMultiplexer.ConnectAsync("127.0.0.1:6379");
            }

            if (string.IsNullOrWhiteSpace(settings.RedisHostname))
            {
                return null;
            }

            var options = new ConfigurationOptions
            {
                EndPoints = { { settings.RedisHostname, settings.RedisPort } },
                Ssl = true,
                AbortOnConnectFail = false,
                Protocol = RedisProtocol.Resp3,
            };
            await options.ConfigureForAzureWithTokenCredentialAsync(new DefaultAzureCredential());
            logger.LogInformation("Connecting to Azure Managed Redis at {Host}:{Port} with Entra auth", settings.RedisHostname, settings.RedisPort);
            return await ConnectionMultiplexer.ConnectAsync(options);
        }
        catch (Exception e)
        {
            logger.LogError(e, "Redis connection failed; falling back to in-memory store");
            return null;
        }
    }
}
