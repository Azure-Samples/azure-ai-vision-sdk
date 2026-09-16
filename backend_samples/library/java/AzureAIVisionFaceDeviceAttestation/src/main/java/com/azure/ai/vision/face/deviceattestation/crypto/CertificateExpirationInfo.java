package com.azure.ai.vision.face.deviceattestation.crypto;

import java.time.Instant;

/** Expiration info from {@link CertUtils#validateCertificateExpiration}. */
public record CertificateExpirationInfo(
        boolean isValid,
        Instant notBefore,
        Instant notAfter,
        boolean isExpired,
        boolean isNotYetValid) {
}
