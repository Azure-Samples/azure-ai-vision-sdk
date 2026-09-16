package com.azure.ai.vision.face.sample.store;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.azure.ai.vision.face.deviceattestation.store.CertificateData;
import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;
import com.azure.ai.vision.face.deviceattestation.store.SessionRecord;
import com.azure.ai.vision.face.deviceattestation.store.Snapshot;
import com.azure.ai.vision.face.deviceattestation.store.StorageException;
import com.azure.ai.vision.face.deviceattestation.store.UpdateResult;

/**
 * Single-instance, non-persistent {@link ClusterStore} used when Redis is not
 * configured/available. Serializes values to JSON with TTL so behavior matches
 * the Redis store exactly.
 */
public final class InMemoryClusterStore implements ClusterStore {

    private record Entry(String json, long expiresAtMillis, String version) {
    }

    private final ConcurrentMap<String, Entry> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Entry> certs = new ConcurrentHashMap<>();
    private final long sessionTtlMs;
    private final long certTtlMs;

    public InMemoryClusterStore(Duration sessionTtl, Duration certTtl) {
        this.sessionTtlMs = sessionTtl.toMillis();
        this.certTtlMs = certTtl.toMillis();
    }

    @Override
    public Snapshot<SessionRecord> getSession(String sid) {
        return read(sessions, sid, SessionRecord.class);
    }

    @Override
    public boolean setSession(String sid, SessionRecord record) {
        return set(sessions, sid, record, sessionTtlMs);
    }

    @Override
    public UpdateResult updateSession(String sid, String expectedVersion, SessionRecord record) {
        return update(sessions, sid, expectedVersion, record);
    }

    @Override
    public Snapshot<CertificateData> getCertificate(String thumbprint) {
        return read(certs, thumbprint, CertificateData.class);
    }

    @Override
    public boolean setCertificate(String thumbprint, CertificateData data) {
        return set(certs, thumbprint, data, certTtlMs);
    }

    @Override
    public UpdateResult updateCertificate(String thumbprint, String expectedVersion, CertificateData data) {
        return update(certs, thumbprint, expectedVersion, data);
    }

    private synchronized <T> Snapshot<T> read(ConcurrentMap<String, Entry> map, String key, Class<T> type) {
        Entry entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiresAtMillis()) {
            map.remove(key);
            return null;
        }
        try {
            return new Snapshot<>(StoreSerialization.MAPPER.readValue(entry.json(), type), entry.version());
        } catch (Exception e) {
            throw new StorageException("Attestation storage unavailable", e);
        }
    }

    private synchronized boolean set(ConcurrentMap<String, Entry> map, String key, Object value, long ttlMs) {
        try {
            if (ttlMs <= 0) throw new StorageException("Invalid attestation TTL");
            Entry existing = map.get(key);
            if (existing != null && existing.expiresAtMillis() > System.currentTimeMillis()) return false;
            map.put(key, new Entry(StoreSerialization.MAPPER.writeValueAsString(value), System.currentTimeMillis() + ttlMs, UUID.randomUUID().toString()));
            return true;
        } catch (Exception e) {
            throw new StorageException("Attestation storage unavailable", e);
        }
    }

    private synchronized UpdateResult update(ConcurrentMap<String, Entry> map, String key, String expectedVersion, Object value) {
        Entry existing = map.get(key);
        if (existing == null || System.currentTimeMillis() >= existing.expiresAtMillis()) {
            map.remove(key);
            return UpdateResult.MISSING_OR_EXPIRED;
        }
        if (!existing.version().equals(expectedVersion)) return UpdateResult.CONFLICT;
        try {
            map.put(key, new Entry(StoreSerialization.MAPPER.writeValueAsString(value), existing.expiresAtMillis(), UUID.randomUUID().toString()));
            return UpdateResult.APPLIED;
        } catch (Exception e) {
            throw new StorageException("Attestation storage unavailable", e);
        }
    }
}
