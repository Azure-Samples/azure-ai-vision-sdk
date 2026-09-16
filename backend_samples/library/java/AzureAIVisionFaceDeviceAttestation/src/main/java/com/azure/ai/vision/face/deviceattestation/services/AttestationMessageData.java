package com.azure.ai.vision.face.deviceattestation.services;

/** Parsed message inputs for attestation verification. */
public final class AttestationMessageData {

    public final String challengeHash;
    public final String clientId;
    public final String system;
    /** PEM-encoded leaf certificate. */
    public final String publicCert;

    public AttestationMessageData(String challengeHash, String clientId, String system, String publicCert) {
        this.challengeHash = challengeHash;
        this.clientId = clientId;
        this.system = system;
        this.publicCert = publicCert;
    }
}
