package com.azure.ai.vision.face.deviceattestation.logging;

import java.util.Map;

/** A logger that discards all telemetry. Used as the default sink. */
public final class NullAttestationLogger implements AttestationLogger {

    /** Shared instance. */
    public static final NullAttestationLogger INSTANCE = new NullAttestationLogger();

    private NullAttestationLogger() {
    }

    @Override
    public void trackEvent(String name, Map<String, Object> properties, Map<String, Double> measurements) {
    }

    @Override
    public void trackException(Throwable error, Map<String, Object> properties) {
    }

    @Override
    public void trackDependency(DependencyTelemetry dependency) {
    }
}
