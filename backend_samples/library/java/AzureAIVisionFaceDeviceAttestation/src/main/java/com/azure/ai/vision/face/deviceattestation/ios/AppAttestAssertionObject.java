package com.azure.ai.vision.face.deviceattestation.ios;

/** Decoded shape of the CBOR assertion object. */
public final class AppAttestAssertionObject {

    public final byte[] signature;
    public final byte[] authenticatorData;

    public AppAttestAssertionObject(byte[] signature, byte[] authenticatorData) {
        this.signature = signature;
        this.authenticatorData = authenticatorData;
    }
}
