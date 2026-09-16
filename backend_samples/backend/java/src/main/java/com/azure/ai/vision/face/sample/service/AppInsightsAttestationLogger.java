package com.azure.ai.vision.face.sample.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.logging.DependencyTelemetry;

/**
 * Adapts the library's {@link AttestationLogger} onto SLF4J. When the
 * Application Insights Java agent is attached it auto-collects these logs (and
 * HTTP requests) with no code changes; otherwise they go to the console.
 */
@Component
public final class AppInsightsAttestationLogger implements AttestationLogger {

    private static final Logger LOG = LoggerFactory.getLogger("attestation");

    @Override
    public void trackEvent(String name, Map<String, Object> properties, Map<String, Double> measurements) {
        LOG.info("event={} properties={}", name, properties);
    }

    @Override
    public void trackException(Throwable error, Map<String, Object> properties) {
        LOG.error("exception properties={}", properties, error);
    }

    @Override
    public void trackDependency(DependencyTelemetry dependency) {
        LOG.info("dependency name={} target={} success={} resultCode={} durationMs={}",
                dependency.name, dependency.target, dependency.success, dependency.resultCode, dependency.durationMillis);
    }
}
