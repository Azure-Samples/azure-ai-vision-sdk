package com.azure.ai.vision.face.deviceattestation.models;

/** Query inputs for the challenge endpoint (built by the host route). */
public final class AttestationChallengeRequest {

    public String sessionId;
    public String clientId;
    public String system;

    public AttestationChallengeRequest() {
    }

    public AttestationChallengeRequest(String sessionId, String clientId, String system) {
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.system = system;
    }
}
