package com.azure.ai.vision.face.deviceattestation.handlers;

/**
 * Standard error response body. {@link #expiredAt} / {@link #validFrom}
 * accompany the certificate-time failures in the register/verify handlers.
 */
public final class ErrorBody {

    /** Human-readable error message. */
    public final String message;

    /** ISO timestamp when a certificate expired (cert-expiry failures); may be null. */
    public final String expiredAt;

    /** ISO timestamp from which a certificate becomes valid (not-yet-valid failures); may be null. */
    public final String validFrom;

    public ErrorBody(String message) {
        this(message, null, null);
    }

    public ErrorBody(String message, String expiredAt, String validFrom) {
        this.message = message;
        this.expiredAt = expiredAt;
        this.validFrom = validFrom;
    }
}
