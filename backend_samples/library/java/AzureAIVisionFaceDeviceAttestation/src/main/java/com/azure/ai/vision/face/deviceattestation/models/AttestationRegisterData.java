package com.azure.ai.vision.face.deviceattestation.models;

import java.util.List;

/**
 * Host-facing attestation details returned alongside a successful register, as
 * {@code outcome.data} (separate from the client body). Populated per platform.
 */
public final class AttestationRegisterData {

    /** "ios" or "android". */
    public String platform;

    /** Non-fatal verification warnings, if any. */
    public List<String> warnings;

    /** Android: Key Attestation chain result (verified to the pinned Google root). */
    public AndroidKeyAttestationInfo androidKeyAttestation;

    /** Android: decoded Play Integrity verdict. */
    public Object integrityVerdict;

    /** iOS: full App Attest verdict (credCert details + the receipt). */
    public Object appAttestVerdict;

    public AttestationRegisterData() {
    }
}
