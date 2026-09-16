package com.azure.ai.vision.face.deviceattestation.models;

/** Host-facing digest result exposed as {@code outcome.data}. */
public final class LivenessDigestData {

    public final String clientDigest;

    public LivenessDigestData(String clientDigest) {
        this.clientDigest = clientDigest;
    }
}
