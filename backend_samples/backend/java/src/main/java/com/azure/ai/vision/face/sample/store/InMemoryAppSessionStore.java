package com.azure.ai.vision.face.sample.store;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory {@link AppSessionStore} (single-instance fallback). */
public final class InMemoryAppSessionStore implements AppSessionStore {

    private record Entry(AppSession session, long expiresAtMillis) {
    }

    private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();
    private final long ttlMs;

    public InMemoryAppSessionStore(Duration ttl) {
        this.ttlMs = ttl.toMillis();
    }

    @Override
    public boolean save(String sid, AppSession session) {
        store.put(sid, new Entry(session, System.currentTimeMillis() + ttlMs));
        return true;
    }

    @Override
    public AppSession get(String sid) {
        Entry entry = store.get(sid);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMillis()) {
            store.remove(sid);
            return null;
        }
        return entry.session();
    }
}
