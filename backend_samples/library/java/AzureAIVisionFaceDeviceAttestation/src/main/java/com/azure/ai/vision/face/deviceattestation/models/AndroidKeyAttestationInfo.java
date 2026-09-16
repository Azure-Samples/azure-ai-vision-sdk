package com.azure.ai.vision.face.deviceattestation.models;

/** Android Key Attestation chain summary returned alongside a register. */
public final class AndroidKeyAttestationInfo {

    public Integer chainLength;
    public String rootCA;
    public String leafCertValidityWarning;

    public AndroidKeyAttestationInfo() {
    }
}
