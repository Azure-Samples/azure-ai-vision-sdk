package com.azure.ai.vision.face.deviceattestation.models;

/** Query + parsed body inputs for the register endpoint (built by the host route). */
public final class AttestationRegisterRequest {

    public String sessionId;
    public String clientId;
    public String system;

    /** Parsed JSON body, or null when the body was absent / not valid JSON. */
    public AttestationRegisterBody body;

    public AttestationRegisterRequest() {
    }

    public AttestationRegisterRequest(String sessionId, String clientId, String system, AttestationRegisterBody body) {
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.system = system;
        this.body = body;
    }
}
