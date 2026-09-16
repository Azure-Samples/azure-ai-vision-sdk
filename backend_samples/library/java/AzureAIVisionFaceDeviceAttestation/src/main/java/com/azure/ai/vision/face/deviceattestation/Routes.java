package com.azure.ai.vision.face.deviceattestation;

/** Canonical route paths, for use as telemetry tags in the host. */
public final class Routes {

    public static final String CHALLENGE = "attestation/challenge";
    public static final String REGISTER = "attestation/register";
    public static final String VERIFY = "attestation/verify";
    public static final String SESSION_TOKEN = "session/token";
    public static final String LIVENESS_DIGEST = "liveness/digest";

    private Routes() {
    }
}
