/*
 * Copyright (c) 2024 Firefly Software Solutions Inc.
 */
package org.fireflyframework.ecm.adapter.adobesign;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
@ConditionalOnProperty(name = "firefly.ecm.esignature.provider", havingValue = "adobe-sign")
@EnableConfigurationProperties(AdobeSignAdapterProperties.class)
public class AdobeSignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "adobeSignWebClient")
    public WebClient adobeSignWebClient(AdobeSignAdapterProperties properties) {
        log.info("Configuring Adobe Sign WebClient with base URL: {}", properties.getBaseUrl());
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("adobeSignCircuitBreaker")
    @ConditionalOnMissingBean(name = "adobeSignCircuitBreaker")
    public CircuitBreaker adobeSignCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build();
        return CircuitBreaker.of("adobeSign", config);
    }

    @Bean("adobeSignRetry")
    @ConditionalOnMissingBean(name = "adobeSignRetry")
    public Retry adobeSignRetry(AdobeSignAdapterProperties properties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.getMaxRetries())
                .waitDuration(Duration.ofSeconds(2))
                .build();
        return Retry.of("adobeSign", config);
    }

    @Bean
    @ConditionalOnMissingBean(SignatureEnvelopePort.class)
    public AdobeSignSignatureEnvelopeAdapter adobeSignSignatureEnvelopeAdapter(
            WebClient adobeSignWebClient,
            AdobeSignAdapterProperties properties,
            ObjectMapper objectMapper,
            DocumentContentPort documentContentPort,
            DocumentPort documentPort,
            CircuitBreaker adobeSignCircuitBreaker,
            Retry adobeSignRetry) {
        log.info("Creating Adobe Sign signature envelope adapter");
        return new AdobeSignSignatureEnvelopeAdapter(
                adobeSignWebClient,
                properties,
                objectMapper,
                documentContentPort,
                documentPort,
                adobeSignCircuitBreaker,
                adobeSignRetry);
    }
}
