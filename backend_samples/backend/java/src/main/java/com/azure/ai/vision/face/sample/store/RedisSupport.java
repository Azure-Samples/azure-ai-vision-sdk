package com.azure.ai.vision.face.sample.store;

import java.time.Duration;

import org.slf4j.Logger;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.RedisCredentialsProviderFactory;

import com.azure.ai.vision.face.sample.config.AppSettings;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;

import io.lettuce.authx.TokenBasedRedisCredentialsProvider;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.TimeoutOptions;
import redis.clients.authentication.core.TokenAuthConfig;
import redis.clients.authentication.entraid.AzureTokenAuthConfigBuilder;

/**
 * Builds a Redis connection: local dev ({@code USE_LOCAL_REDIS=true}) or Azure
 * Managed Redis with Entra ID auth (no access keys). Returns null when Redis is
 * not configured or unreachable so the caller can fall back to in-memory.
 */
public final class RedisSupport {

    private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(30);

    public static LettuceConnectionFactory tryCreate(AppSettings settings, Logger log) {
        boolean local = settings.isUseLocalRedis();
        boolean azure = !local && settings.getRedisHostname() != null && !settings.getRedisHostname().isBlank();
        if (!local && !azure) {
            return null;
        }

        TokenBasedRedisCredentialsProvider credentials = null;
        LettuceConnectionFactory factory = null;
        try {
            RedisStandaloneConfiguration config = local
                    ? new RedisStandaloneConfiguration("127.0.0.1", 6379)
                    : new RedisStandaloneConfiguration(settings.getRedisHostname(), settings.getRedisPort());

            if (azure) {
                credentials = TokenBasedRedisCredentialsProvider.create(
                    createTokenAuthConfig(new DefaultAzureCredentialBuilder().build()));
                if (credentials.resolveCredentials().block(TOKEN_TIMEOUT) == null) {
                    throw new IllegalStateException("Could not acquire Entra credentials for Redis");
                }
            }

            factory = createConnectionFactory(config, credentials);
            factory.afterPropertiesSet();
            try (RedisConnection connection = factory.getConnection()) {
                connection.ping();
            }
            log.info("Connected to Redis ({}).", local ? "local" : settings.getRedisHostname());
            return factory;
        } catch (Exception e) {
            try {
                if (factory != null) {
                    factory.destroy();
                } else if (credentials != null) {
                    credentials.close();
                }
            } catch (Exception cleanupError) {
                e.addSuppressed(cleanupError);
            }
            log.warn("Redis not available ({}); using in-memory stores.", e.getMessage());
            return null;
        }
    }

    static TokenAuthConfig createTokenAuthConfig(DefaultAzureCredential credential) {
        try (var builder = AzureTokenAuthConfigBuilder.builder()) {
            return builder.defaultAzureCredential(credential)
                    .tokenRequestExecTimeoutInMs((int) TOKEN_TIMEOUT.toMillis())
                    .maxAttemptsToRetry(Integer.MAX_VALUE)
                    .delayInMsToRetry(30_000)
                    .build();
        }
    }

    static LettuceConnectionFactory createConnectionFactory(RedisStandaloneConfiguration config,
            TokenBasedRedisCredentialsProvider credentials) {
        var client = LettuceClientConfiguration.builder();
        if (credentials != null) {
            client.useSsl();
            client.clientOptions(ClientOptions.builder()
                    .timeoutOptions(TimeoutOptions.enabled())
                    .reauthenticateBehavior(ClientOptions.ReauthenticateBehavior.ON_NEW_CREDENTIALS)
                    .build());
            client.redisCredentialsProviderFactory(new RedisCredentialsProviderFactory() {
                @Override
                public RedisCredentialsProvider createCredentialsProvider(RedisConfiguration configuration) {
                    return credentials;
                }
            });
        }
        return new LettuceConnectionFactory(config, client.build()) {
            @Override
            public void destroy() {
                try {
                    super.destroy();
                } finally {
                    if (credentials != null) {
                        credentials.close();
                    }
                }
            }
        };
    }

    private RedisSupport() {
    }
}
