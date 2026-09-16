package com.azure.ai.vision.face.deviceattestation.crypto;

import java.time.Instant;

/** Parsed validity result from {@link CertUtils#validateCertificate}. */
public record CertificateValidationResult(
        boolean valid,
        String subject,
        String issuer,
        Instant validFrom,
        Instant validTo) {
}
