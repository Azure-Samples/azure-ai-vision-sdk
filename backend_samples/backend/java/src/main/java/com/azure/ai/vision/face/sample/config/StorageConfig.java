package com.azure.ai.vision.face.sample.config;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;
import com.azure.ai.vision.face.sample.store.AppSessionStore;
import com.azure.ai.vision.face.sample.store.InMemoryAppSessionStore;
import com.azure.ai.vision.face.sample.store.InMemoryClusterStore;
import com.azure.ai.vision.face.sample.store.RedisAppSessionStore;
import com.azure.ai.vision.face.sample.store.RedisClusterStore;
import com.azure.ai.vision.face.sample.store.RedisSupport;

/**
 * Pluggable storage: Redis when available, else in-memory. The attestation
 * library persists through {@link ClusterStore}, so swapping storage is the only
 * change a consumer makes.
 */
@Configuration
public class StorageConfig {

    private static final Logger LOG = LoggerFactory.getLogger(StorageConfig.class);

    /** Holds the two store implementations chosen at startup. */
    public record StoreBundle(ClusterStore clusterStore, AppSessionStore appSessionStore) {
    }

    @Bean(destroyMethod = "destroy")
    public LettuceConnectionFactory redisConnectionFactory(AppSettings settings) {
        return RedisSupport.tryCreate(settings, LOG);
    }

    @Bean
    public StoreBundle storeBundle(AppSettings settings, Optional<LettuceConnectionFactory> connectionFactory) {
        Duration sessionTtl = Duration.ofSeconds(settings.getSessionTokenTtl());
        Duration certTtl = Duration.ofSeconds(settings.getCertTtl());

        LettuceConnectionFactory factory = connectionFactory.orElse(null);
        if (factory != null) {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            return new StoreBundle(
                    new RedisClusterStore(redis, sessionTtl, certTtl),
                    new RedisAppSessionStore(redis, sessionTtl));
        }
        LOG.warn("Using in-memory stores (single-instance, non-persistent).");
        return new StoreBundle(
                new InMemoryClusterStore(sessionTtl, certTtl),
                new InMemoryAppSessionStore(sessionTtl));
    }

    @Bean
    public ClusterStore clusterStore(StoreBundle bundle) {
        return bundle.clusterStore();
    }

    @Bean
    public AppSessionStore appSessionStore(StoreBundle bundle) {
        return bundle.appSessionStore();
    }
}
