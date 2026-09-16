package com.azure.ai.vision.face.deviceattestation.models;

/** Success body: acknowledgement + the server's encryption public key. */
public final class AttestationRegisterSuccess {

    public final String message;
    public final String serverEncryptionPublicKey;

    public AttestationRegisterSuccess(String message, String serverEncryptionPublicKey) {
        this.message = message;
        this.serverEncryptionPublicKey = serverEncryptionPublicKey;
    }
}
