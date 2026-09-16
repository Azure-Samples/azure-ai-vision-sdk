package com.azure.ai.vision.face.deviceattestation.ios;

/** Decoded shape of the CBOR attestation object. */
public final class AppAttestObject {

    public final String fmt;
    public final byte[] authData;
    public final byte[] credCertDer;
    public final byte[] intermediateDer;
    public final int receiptLength;
    public final byte[] receipt;

    public AppAttestObject(String fmt, byte[] authData, byte[] credCertDer, byte[] intermediateDer,
            int receiptLength, byte[] receipt) {
        this.fmt = fmt;
        this.authData = authData;
        this.credCertDer = credCertDer;
        this.intermediateDer = intermediateDer;
        this.receiptLength = receiptLength;
        this.receipt = receipt;
    }
}
