package com.azure.ai.vision.face.deviceattestation.models;

/** JSON body accepted by the token endpoint. {@code assertion} is required on iOS. */
public final class SessionTokenBody {

    public String encryptedData;
    public String signature;
    public String assertion;

    public SessionTokenBody() {
    }
}
