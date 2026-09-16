package com.azure.ai.vision.face.sample.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.azure.ai.vision.face.deviceattestation.AttestationService;
import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;

/** Builds the {@link AttestationService} singleton: config + store + logger. */
@Configuration
public class AttestationConfiguration {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public AttestationService attestationService(AppSettings settings, ClusterStore store, AttestationLogger logger) {
        return AttestationService.create(settings.toAttestationConfig(), store, logger);
    }
}
