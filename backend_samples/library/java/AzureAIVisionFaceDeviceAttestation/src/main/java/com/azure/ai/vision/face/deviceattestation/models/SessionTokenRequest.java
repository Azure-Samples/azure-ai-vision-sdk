package com.azure.ai.vision.face.deviceattestation.models;

/** Query + parsed body inputs for the token endpoint (built by the host route). */
public final class SessionTokenRequest {

    public String sessionId;
    public SessionTokenBody body;

    public SessionTokenRequest() {
    }

    public SessionTokenRequest(String sessionId, SessionTokenBody body) {
        this.sessionId = sessionId;
        this.body = body;
    }
}
