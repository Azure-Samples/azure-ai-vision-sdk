package com.azure.ai.vision.face.deviceattestation.models;

/** Success body: the Tink ECIES blob (base64) carrying the encrypted ack. */
public final class LivenessDigestSuccess {

    public final String encryptedData;

    public LivenessDigestSuccess(String encryptedData) {
        this.encryptedData = encryptedData;
    }
}
