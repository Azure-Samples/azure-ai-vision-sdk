package com.azure.ai.vision.face.deviceattestation.models;

/** Success body: the freshly issued challenge, echoing the bound identity. */
public final class AttestationChallengeSuccess {

    public final String challengeHash;
    public final String clientId;
    public final String system;

    public AttestationChallengeSuccess(String challengeHash, String clientId, String system) {
        this.challengeHash = challengeHash;
        this.clientId = clientId;
        this.system = system;
    }
}
