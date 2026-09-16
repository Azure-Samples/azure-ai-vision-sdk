package com.azure.ai.vision.face.sample.store;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.azure.ai.vision.face.deviceattestation.store.CertificateData;
import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;
import com.azure.ai.vision.face.deviceattestation.store.SessionRecord;
import com.azure.ai.vision.face.deviceattestation.store.Snapshot;
import com.azure.ai.vision.face.deviceattestation.store.StorageException;
import com.azure.ai.vision.face.deviceattestation.store.UpdateResult;

/**
 * Redis-backed {@link ClusterStore}. Owns the key schema + TTL policy:
 * {@code attestation/<sid>} (sessions) and {@code cert_thumb:<thumbprint>}
 * (certificates). {@code update*} preserves the remaining TTL and fails closed
 * if the entry has expired.
 */
public final class RedisClusterStore implements ClusterStore {

        private static final String SESSION_PREFIX = "attestation:v2/";
        private static final String CERT_PREFIX = "cert_thumb:v2:";
        private static final DefaultRedisScript<Long> CAS_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or redis.call('PTTL', KEYS[1]) <= 0 then return 2 end
            if cjson.decode(current).version ~= ARGV[1] then return 1 end
            redis.call('SET', KEYS[1], ARGV[2], 'XX', 'KEEPTTL')
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Duration sessionTtl;
    private final Duration certTtl;

    public RedisClusterStore(StringRedisTemplate redis, Duration sessionTtl, Duration certTtl) {
        this.redis = redis;
        this.sessionTtl = sessionTtl;
        this.certTtl = certTtl;
    }

    @Override
    public Snapshot<SessionRecord> getSession(String sid) {
        return read(SESSION_PREFIX + sid, SessionRecord.class);
    }

    @Override
    public boolean setSession(String sid, SessionRecord record) {
        return set(SESSION_PREFIX + sid, record, sessionTtl);
    }

    @Override
    public UpdateResult updateSession(String sid, String expectedVersion, SessionRecord record) {
        return update(SESSION_PREFIX + sid, expectedVersion, record);
    }

    @Override
    public Snapshot<CertificateData> getCertificate(String thumbprint) {
        return read(CERT_PREFIX + thumbprint, CertificateData.class);
    }

    @Override
    public boolean setCertificate(String thumbprint, CertificateData data) {
        return set(CERT_PREFIX + thumbprint, data, certTtl);
    }

    @Override
    public UpdateResult updateCertificate(String thumbprint, String expectedVersion, CertificateData data) {
        return update(CERT_PREFIX + thumbprint, expectedVersion, data);
    }

    private <T> Snapshot<T> read(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) return null;
            Snapshot<T> snapshot = StoreSerialization.MAPPER.readValue(json,
                    StoreSerialization.MAPPER.getTypeFactory().constructParametricType(Snapshot.class, type));
            if (snapshot == null || snapshot.value() == null || snapshot.version() == null || snapshot.version().isEmpty()) {
                throw new StorageException("Invalid versioned attestation record");
            }
            return snapshot;
        } catch (Exception e) {
            throw new StorageException("Attestation storage unavailable", e);
        }
    }

    private boolean set(String key, Object value, Duration ttl) {
        try {
            if (ttl.isZero() || ttl.isNegative()) throw new StorageException("Invalid attestation TTL");
            Boolean created = redis.opsForValue().setIfAbsent(key,
                    StoreSerialization.MAPPER.writeValueAsString(new Snapshot<>(value, UUID.randomUUID().toString())), ttl);
            if (created == null) throw new StorageException("Unknown creation outcome");
            return created;
        } catch (Exception e) {
            throw new StorageException("Attestation storage unavailable", e);
        }
    }

    private UpdateResult update(String key, String expectedVersion, Object value) {
        try {
            Long result = redis.execute(CAS_SCRIPT, List.of(key), expectedVersion,
                    StoreSerialization.MAPPER.writeValueAsString(new Snapshot<>(value, UUID.randomUUID().toString())));
            if (result == null) throw new StorageException("Unknown CAS outcome");
            return switch (result.intValue()) {
                case 0 -> UpdateResult.APPLIED;
                case 1 -> UpdateResult.CONFLICT;
                case 2 -> UpdateResult.MISSING_OR_EXPIRED;
                default -> throw new StorageException("Unexpected CAS outcome");
            };
        } catch (Exception e) {
            throw new StorageException("Attestation storage unavailable", e);
        }
    }
}
