package com.azure.ai.vision.face.deviceattestation.models;

/** JSON body accepted by the digest endpoint. {@code assertion} is required on iOS. */
public final class LivenessDigestBody {

    public String encryptedData;
    public String signature;
    public String assertion;

    public LivenessDigestBody() {
    }
}
