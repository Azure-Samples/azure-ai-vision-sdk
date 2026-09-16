package com.azure.ai.vision.face.deviceattestation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;

import com.azure.ai.vision.face.deviceattestation.store.CertificateData;
import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;
import com.azure.ai.vision.face.deviceattestation.store.SessionRecord;
import com.azure.ai.vision.face.deviceattestation.store.Snapshot;
import com.azure.ai.vision.face.deviceattestation.store.UpdateResult;

/** Non-persistent in-memory {@link ClusterStore} for tests (no TTL). */
public final class TestClusterStore implements ClusterStore {

    private final Map<String, Snapshot<SessionRecord>> sessions = new HashMap<>();
    private final Map<String, Snapshot<CertificateData>> certs = new HashMap<>();
    private static final Gson JSON = new Gson();
    public Runnable afterSessionRead;
    public boolean failWrites;

    @Override
    public Snapshot<SessionRecord> getSession(String sid) {
        Snapshot<SessionRecord> result;
        synchronized (this) {
            var snapshot = sessions.get(sid);
            result = snapshot == null ? null : new Snapshot<>(JSON.fromJson(JSON.toJson(snapshot.value()), SessionRecord.class), snapshot.version());
        }
        if (afterSessionRead != null) afterSessionRead.run();
        return result;
    }

    @Override
    public synchronized boolean setSession(String sid, SessionRecord record) {
        if (sessions.containsKey(sid)) return false;
        sessions.put(sid, new Snapshot<>(JSON.fromJson(JSON.toJson(record), SessionRecord.class), UUID.randomUUID().toString()));
        return true;
    }

    @Override
    public synchronized UpdateResult updateSession(String sid, String expectedVersion, SessionRecord record) {
        if (failWrites) throw new com.azure.ai.vision.face.deviceattestation.store.StorageException("Injected storage failure");
        if (!sessions.containsKey(sid)) {
            return UpdateResult.MISSING_OR_EXPIRED;
        }
        if (!sessions.get(sid).version().equals(expectedVersion)) return UpdateResult.CONFLICT;
        sessions.put(sid, new Snapshot<>(JSON.fromJson(JSON.toJson(record), SessionRecord.class), UUID.randomUUID().toString()));
        return UpdateResult.APPLIED;
    }

    @Override
    public synchronized Snapshot<CertificateData> getCertificate(String thumbprint) {
        var snapshot = certs.get(thumbprint);
        return snapshot == null ? null : new Snapshot<>(JSON.fromJson(JSON.toJson(snapshot.value()), CertificateData.class), snapshot.version());
    }

    @Override
    public synchronized boolean setCertificate(String thumbprint, CertificateData data) {
        if (certs.containsKey(thumbprint)) return false;
        certs.put(thumbprint, new Snapshot<>(JSON.fromJson(JSON.toJson(data), CertificateData.class), UUID.randomUUID().toString()));
        return true;
    }

    @Override
    public synchronized UpdateResult updateCertificate(String thumbprint, String expectedVersion, CertificateData data) {
        if (!certs.containsKey(thumbprint)) {
            return UpdateResult.MISSING_OR_EXPIRED;
        }
        if (!certs.get(thumbprint).version().equals(expectedVersion)) return UpdateResult.CONFLICT;
        certs.put(thumbprint, new Snapshot<>(JSON.fromJson(JSON.toJson(data), CertificateData.class), UUID.randomUUID().toString()));
        return UpdateResult.APPLIED;
    }
}
