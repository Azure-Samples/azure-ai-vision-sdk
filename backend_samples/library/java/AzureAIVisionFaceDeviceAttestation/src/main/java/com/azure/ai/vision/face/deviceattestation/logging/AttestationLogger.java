package com.azure.ai.vision.face.deviceattestation.logging;

import java.util.Map;

/**
 * Telemetry sink the host provides so the library can emit telemetry without
 * depending on a concrete SDK. The host installs an implementation (e.g. App
 * Insights) when constructing the {@code AttestationService}; if none is
 * supplied a no-op logger is used so unconfigured/test runs stay silent.
 */
public interface AttestationLogger {

    /** Record a named custom event with optional properties + numeric measurements. */
    void trackEvent(String name, Map<String, Object> properties, Map<String, Double> measurements);

    /** Record an exception with optional properties. */
    void trackException(Throwable error, Map<String, Object> properties);

    /** Record an outbound dependency call. */
    void trackDependency(DependencyTelemetry dependency);
}
