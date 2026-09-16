package com.azure.ai.vision.face.deviceattestation.models;

/** JSON body accepted by the register endpoint. */
public final class AttestationRegisterBody {

    public String payload;
    public String authPublicCert;
    public String signature;

    public AttestationRegisterBody() {
    }
}
