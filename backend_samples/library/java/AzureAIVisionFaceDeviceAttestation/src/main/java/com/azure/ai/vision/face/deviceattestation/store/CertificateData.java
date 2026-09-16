package com.azure.ai.vision.face.deviceattestation.store;

import java.util.Map;

/**
 * Certificate record keyed by the SHA-256 thumbprint of the certificate DER.
 * The metadata blob holds the platform-specific verdict (PlayIntegrityVerdict /
 * AppAttestVerdict) plus the assertion sign-count bookkeeping.
 */
public final class CertificateData {

    /** Client application ID that registered the certificate. */
    public String clientId;

    /** Platform: "ios" or "android". */
    public String system;

    /** SHA-256 thumbprint of the certificate DER (64 hex chars). */
    public String thumbprint;

    /** Certificate in PEM format. */
    public String publicCert;

    /** ISO 8601 creation timestamp. */
    public String createdAt;

    /** ISO 8601 last-verified timestamp. */
    public String lastVerifiedAt;

    /** Platform verdict + per-call assertion bookkeeping; may be null. */
    public Map<String, Object> metadata;

    public CertificateData() {
    }
}
