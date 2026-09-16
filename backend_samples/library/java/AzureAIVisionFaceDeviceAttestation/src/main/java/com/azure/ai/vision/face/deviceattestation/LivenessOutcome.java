package com.azure.ai.vision.face.deviceattestation;

/**
 * Liveness-completion signal for a session: whether the client has posted its
 * digest yet and, if so, the digest it submitted.
 */
public final class LivenessOutcome {

    /** Whether the client has posted its liveness digest. */
    public final boolean completed;

    /** The digest the client submitted, if completed; otherwise null. */
    public final String clientDigest;

    public LivenessOutcome(boolean completed, String clientDigest) {
        this.completed = completed;
        this.clientDigest = clientDigest;
    }
}
