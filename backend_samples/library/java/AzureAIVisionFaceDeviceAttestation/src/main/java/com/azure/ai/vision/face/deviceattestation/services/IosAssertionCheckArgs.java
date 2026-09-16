package com.azure.ai.vision.face.deviceattestation.services;

/** Inputs for the shared iOS assertion check. */
public record IosAssertionCheckArgs(
        String routeName,
        String sessionId,
        String thumbprint,
        byte[] blob,
        String assertion) {
}
