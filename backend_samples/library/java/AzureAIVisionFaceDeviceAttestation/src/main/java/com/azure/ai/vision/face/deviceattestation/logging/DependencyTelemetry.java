package com.azure.ai.vision.face.deviceattestation.logging;

import java.util.Map;

/** An outbound dependency call to record (e.g. Play Integrity / revocation HTTP). */
public final class DependencyTelemetry {

    /** Logical name of the dependency call. */
    public final String name;

    /** Remote target (host, service name, …); may be null. */
    public final String target;

    /** Command/data associated with the call; may be null. */
    public final String data;

    /** How long the call took, in milliseconds. */
    public final long durationMillis;

    /** Whether the call succeeded. */
    public final boolean success;

    /** Result/status code, if any; may be null. */
    public final String resultCode;

    /** Dependency type name (e.g. "HTTP", "Redis"); may be null. */
    public final String dependencyTypeName;

    /** Optional structured properties; may be null. */
    public final Map<String, Object> properties;

    private DependencyTelemetry(Builder b) {
        this.name = b.name;
        this.target = b.target;
        this.data = b.data;
        this.durationMillis = b.durationMillis;
        this.success = b.success;
        this.resultCode = b.resultCode;
        this.dependencyTypeName = b.dependencyTypeName;
        this.properties = b.properties;
    }

    public static Builder builder(String name) { return new Builder(name); }

    /** Fluent builder for {@link DependencyTelemetry}. */
    public static final class Builder {
        private final String name;
        private String target;
        private String data;
        private long durationMillis;
        private boolean success;
        private String resultCode;
        private String dependencyTypeName;
        private Map<String, Object> properties;

        private Builder(String name) { this.name = name; }

        public Builder target(String v) { this.target = v; return this; }
        public Builder data(String v) { this.data = v; return this; }
        public Builder durationMillis(long v) { this.durationMillis = v; return this; }
        public Builder success(boolean v) { this.success = v; return this; }
        public Builder resultCode(String v) { this.resultCode = v; return this; }
        public Builder dependencyTypeName(String v) { this.dependencyTypeName = v; return this; }
        public Builder properties(Map<String, Object> v) { this.properties = v; return this; }

        public DependencyTelemetry build() { return new DependencyTelemetry(this); }
    }
}
