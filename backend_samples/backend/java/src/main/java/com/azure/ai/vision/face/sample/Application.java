package com.azure.ai.vision.face.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;

import com.azure.ai.vision.face.sample.config.ApiRoutes;
import com.azure.ai.vision.face.sample.config.AppSettings;

/**
 * Spring Boot host for the Azure AI Vision Face device-attestation backend
 * library. Owns storage, telemetry, and the Face REST client; the library owns
 * the attestation verification and the /.well-known documents.
 */
@SpringBootApplication(exclude = { DataRedisAutoConfiguration.class, DataRedisReactiveAutoConfiguration.class,
    DataRedisRepositoriesAutoConfiguration.class })
@EnableConfigurationProperties({ AppSettings.class, ApiRoutes.class })
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
