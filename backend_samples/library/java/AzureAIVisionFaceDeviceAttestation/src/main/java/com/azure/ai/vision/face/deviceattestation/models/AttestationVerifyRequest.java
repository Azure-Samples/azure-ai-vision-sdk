package com.azure.ai.vision.face.deviceattestation.models;

/** Query + parsed body inputs for the verify endpoint (built by the host route). */
public final class AttestationVerifyRequest {

    public String sessionId;
    public String clientId;
    public String system;
    public AttestationVerifyBody body;

    public AttestationVerifyRequest() {
    }

    public AttestationVerifyRequest(String sessionId, String clientId, String system, AttestationVerifyBody body) {
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.system = system;
        this.body = body;
    }
}
