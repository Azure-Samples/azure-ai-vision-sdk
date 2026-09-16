package com.azure.ai.vision.face.deviceattestation.services;

import java.util.List;

/** Platform-agnostic result of an attestation verification. */
public final class AuthVerificationResult {

    public final boolean verified;
    public final String platform;
    public final String message;
    public final String timestamp;
    public final Integer chainLength;
    public final String rootCA;
    public final Object integrityVerdict;
    public final Object appAttestVerdict;
    public final String leafCertValidityWarning;
    public final List<String> warnings;

    private AuthVerificationResult(Builder b) {
        this.verified = b.verified;
        this.platform = b.platform;
        this.message = b.message;
        this.timestamp = b.timestamp;
        this.chainLength = b.chainLength;
        this.rootCA = b.rootCA;
        this.integrityVerdict = b.integrityVerdict;
        this.appAttestVerdict = b.appAttestVerdict;
        this.leafCertValidityWarning = b.leafCertValidityWarning;
        this.warnings = b.warnings;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link AuthVerificationResult}. */
    public static final class Builder {
        private boolean verified;
        private String platform = "unknown";
        private String message = "";
        private String timestamp = "";
        private Integer chainLength;
        private String rootCA;
        private Object integrityVerdict;
        private Object appAttestVerdict;
        private String leafCertValidityWarning;
        private List<String> warnings;

        public Builder verified(boolean v) { this.verified = v; return this; }
        public Builder platform(String v) { this.platform = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder timestamp(String v) { this.timestamp = v; return this; }
        public Builder chainLength(Integer v) { this.chainLength = v; return this; }
        public Builder rootCA(String v) { this.rootCA = v; return this; }
        public Builder integrityVerdict(Object v) { this.integrityVerdict = v; return this; }
        public Builder appAttestVerdict(Object v) { this.appAttestVerdict = v; return this; }
        public Builder leafCertValidityWarning(String v) { this.leafCertValidityWarning = v; return this; }
        public Builder warnings(List<String> v) { this.warnings = v; return this; }

        public AuthVerificationResult build() { return new AuthVerificationResult(this); }
    }
}
