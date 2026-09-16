package com.azure.ai.vision.face.deviceattestation.store;

import java.util.Map;

/**
 * Value stored under a session UUID: the Face session token plus the session
 * state object. The store owns how this record is serialized on the wire —
 * callers only ever see this structured shape, never a storage format.
 */
public final class SessionRecord {

    /** The Face session token seeded when the session is created. */
    public String token;

    /**
     * Mutable attestation session state (challenge hash, exchanged keys, cert
     * thumbprint, and the completion flags). Modeled as a plain map so the store
     * can persist it verbatim.
     */
    public Map<String, Object> data;

    public SessionRecord() {
    }

    public SessionRecord(String token, Map<String, Object> data) {
        this.token = token;
        this.data = data;
    }
}
