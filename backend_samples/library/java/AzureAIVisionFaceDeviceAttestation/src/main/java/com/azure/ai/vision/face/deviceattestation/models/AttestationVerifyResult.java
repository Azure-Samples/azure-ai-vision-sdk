package com.azure.ai.vision.face.deviceattestation.models;

/** Success body: whether the cert exists and, if so, the server's enc public key. */
public final class AttestationVerifyResult {

    public final boolean exists;
    public final String serverEncryptionPublicKey;

    public AttestationVerifyResult(boolean exists, String serverEncryptionPublicKey) {
        this.exists = exists;
        this.serverEncryptionPublicKey = serverEncryptionPublicKey;
    }
}
