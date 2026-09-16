package com.azure.ai.vision.face.deviceattestation;

import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;

/**
 * The injected dependencies threaded through the handlers and platform
 * verifiers: runtime config, the persistent store, and the telemetry sink.
 * Replaces the npm library's mutable module-global config/logger with explicit
 * dependency injection.
 */
public final class AttestationContext {

    public final AttestationConfig config;
    public final ClusterStore store;
    public final AttestationLogger logger;

    public AttestationContext(AttestationConfig config, ClusterStore store, AttestationLogger logger) {
        this.config = config;
        this.store = store;
        this.logger = logger;
    }
}
