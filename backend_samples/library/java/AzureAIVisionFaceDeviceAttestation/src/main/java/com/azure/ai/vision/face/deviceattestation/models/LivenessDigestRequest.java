package com.azure.ai.vision.face.deviceattestation.models;

/** Query + parsed body inputs for the digest endpoint (built by the host route). */
public final class LivenessDigestRequest {

    public String sessionId;
    public LivenessDigestBody body;

    public LivenessDigestRequest() {
    }

    public LivenessDigestRequest(String sessionId, LivenessDigestBody body) {
        this.sessionId = sessionId;
        this.body = body;
    }
}
