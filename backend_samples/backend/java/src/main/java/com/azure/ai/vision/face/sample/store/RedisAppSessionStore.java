package com.azure.ai.vision.face.sample.store;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed {@link AppSessionStore}. Key schema {@code session/<sid>}. */
public final class RedisAppSessionStore implements AppSessionStore {

    private static final String PREFIX = "session/";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisAppSessionStore(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public boolean save(String sid, AppSession session) {
        try {
            redis.opsForValue().set(PREFIX + sid, StoreSerialization.MAPPER.writeValueAsString(session), ttl);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AppSession get(String sid) {
        String json = redis.opsForValue().get(PREFIX + sid);
        if (json == null) {
            return null;
        }
        try {
            return StoreSerialization.MAPPER.readValue(json, AppSession.class);
        } catch (Exception e) {
            return null;
        }
    }
}
