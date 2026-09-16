package com.azure.ai.vision.face.deviceattestation.models;

/** JSON body accepted by the verify endpoint. */
public final class AttestationVerifyBody {

    public String payload;
    public String authPublicCert;
    public String signature;

    /** Top-level base64 CBOR assertion (iOS only), required once registered. */
    public String assertion;

    public AttestationVerifyBody() {
    }
}
