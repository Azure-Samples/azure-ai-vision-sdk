package com.azure.ai.vision.face.deviceattestation.models;

/** Success body: the Tink ECIES blob (base64) carrying the encrypted response. */
public final class SessionTokenSuccess {

    public final String encryptedData;

    public SessionTokenSuccess(String encryptedData) {
        this.encryptedData = encryptedData;
    }
}
