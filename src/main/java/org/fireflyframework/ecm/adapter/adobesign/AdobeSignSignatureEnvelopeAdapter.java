/*
 * Copyright (c) 2024 Firefly Software Solutions Inc.
 */
package org.fireflyframework.ecm.adapter.adobesign;

import org.fireflyframework.ecm.adapter.AdapterFeature;
import org.fireflyframework.ecm.adapter.EcmAdapter;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.domain.enums.esignature.EnvelopeStatus;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.reactor.retry.RetryOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@EcmAdapter(
    type = "adobe-sign",
    description = "Adobe Sign eSignature Envelope Adapter",
    supportedFeatures = {
        AdapterFeature.ESIGNATURE_ENVELOPES,
        AdapterFeature.ESIGNATURE_REQUESTS,
        AdapterFeature.SIGNATURE_VALIDATION
    },
    requiredProperties = {"client-id", "client-secret", "refresh-token"},
    optionalProperties = {"base-url", "api-version", "webhook-url", "webhook-secret", "return-url"}
)
@Component
@ConditionalOnProperty(name = "firefly.ecm.esignature.provider", havingValue = "adobe-sign")
public class AdobeSignSignatureEnvelopeAdapter implements SignatureEnvelopePort {

    private final WebClient webClient;
    private final AdobeSignAdapterProperties properties;
    private final ObjectMapper objectMapper;
    private final DocumentContentPort documentContentPort;
    private final DocumentPort documentPort;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    private final Map<UUID, String> envelopeIdMapping = new ConcurrentHashMap<>();
    private final Map<String, UUID> externalIdMapping = new ConcurrentHashMap<>();

    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;

    public AdobeSignSignatureEnvelopeAdapter(WebClient webClient,
                                           AdobeSignAdapterProperties properties,
                                           ObjectMapper objectMapper,
                                           DocumentContentPort documentContentPort,
                                           DocumentPort documentPort,
                                           @Qualifier("adobeSignCircuitBreaker") CircuitBreaker circuitBreaker,
                                           @Qualifier("adobeSignRetry") Retry retry) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.documentContentPort = documentContentPort;
        this.documentPort = documentPort;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public Mono<SignatureEnvelope> createEnvelope(SignatureEnvelope envelope) {
        return ensureValidAccessToken()
            .flatMap(token -> buildAgreementRequest(envelope)
                .flatMap(agreement -> webClient.post()
                    .uri("/api/rest/{apiVersion}/agreements", properties.getApiVersion())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(agreement)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                )
                .map(resp -> {
                    String agreementId = resp.get("id").asText();
                    UUID id = envelope.getId() != null ? envelope.getId() : UUID.randomUUID();
                    envelopeIdMapping.put(id, agreementId);
                    externalIdMapping.put(agreementId, id);
                    return envelope.toBuilder()
                        .id(id)
                        .provider(SignatureProvider.ADOBE_SIGN)
                        .status(EnvelopeStatus.DRAFT)
                        .externalEnvelopeId(agreementId)
                        .createdAt(Instant.now())
                        .build();
                })
            )
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry));
    }

    @Override
    public Mono<SignatureEnvelope> getEnvelope(UUID envelopeId) {
        return ensureValidAccessToken()
            .flatMap(token -> {
                String agreementId = envelopeIdMapping.get(envelopeId);
                if (agreementId == null) return Mono.error(new RuntimeException("Envelope not found"));
                return webClient.get()
                    .uri("/api/rest/{apiVersion}/agreements/{id}", properties.getApiVersion(), agreementId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(node -> SignatureEnvelope.builder()
                        .id(envelopeId)
                        .provider(SignatureProvider.ADOBE_SIGN)
                        .title(node.path("name").asText(null))
                        .status(EnvelopeStatus.valueOf(node.path("status").asText("DRAFT").toUpperCase()))
                        .externalEnvelopeId(agreementId)
                        .build());
            });
    }

    private Mono<String> ensureValidAccessToken() {
        if (accessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return Mono.just(accessToken);
        }
        return webClient.post()
            .uri("/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue("grant_type=refresh_token&client_id=" + properties.getClientId() +
                       "&client_secret=" + properties.getClientSecret() +
                       "&refresh_token=" + properties.getRefreshToken())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(token -> {
                this.accessToken = token.get("access_token").asText();
                this.tokenExpiresAt = Instant.now().plusSeconds(token.get("expires_in").asLong(3600));
                return this.accessToken;
            });
    }

    private Mono<Map<String, Object>> buildAgreementRequest(SignatureEnvelope envelope) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("name", Optional.ofNullable(envelope.getTitle()).orElse("Agreement"));
        payload.put("message", Optional.ofNullable(envelope.getDescription()).orElse(""));
        // Minimal example; map documents and participants as needed
        return Mono.just(payload);
    }

    @Override
    public Mono<SignatureEnvelope> updateEnvelope(SignatureEnvelope envelope) {
        return getEnvelope(envelope.getId());
    }

    @Override
    public Mono<Void> deleteEnvelope(UUID envelopeId) {
        return Mono.empty();
    }

    @Override
    public Mono<SignatureEnvelope> sendEnvelope(UUID envelopeId, UUID sentBy) {
        return getEnvelope(envelopeId);
    }

    @Override
    public Mono<SignatureEnvelope> voidEnvelope(UUID envelopeId, String voidReason, UUID voidedBy) {
        return getEnvelope(envelopeId);
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByStatus(EnvelopeStatus status, Integer limit) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByCreator(UUID createdBy, Integer limit) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesBySender(UUID sentBy, Integer limit) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getEnvelopesByProvider(SignatureProvider provider, Integer limit) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getExpiringEnvelopes(Instant fromTime, Instant toTime) {
        return Flux.empty();
    }

    @Override
    public Flux<SignatureEnvelope> getCompletedEnvelopes(Instant fromTime, Instant toTime) {
        return Flux.empty();
    }

    @Override
    public Mono<Boolean> existsEnvelope(UUID envelopeId) {
        return Mono.just(envelopeId != null && envelopeIdMapping.containsKey(envelopeId));
    }

    @Override
    public Mono<SignatureEnvelope> getEnvelopeByExternalId(String externalEnvelopeId, SignatureProvider provider) {
        UUID id = externalIdMapping.get(externalEnvelopeId);
        return id != null ? getEnvelope(id) : Mono.empty();
    }

    @Override
    public Mono<SignatureEnvelope> syncEnvelopeStatus(UUID envelopeId) {
        return getEnvelope(envelopeId);
    }

    @Override
    public Mono<String> getSigningUrl(UUID envelopeId, String signerEmail, String signerName, String clientUserId) {
        return Mono.error(new UnsupportedOperationException("Embedded signing URL not implemented for Adobe Sign"));
    }

    @Override
    public Mono<Void> resendEnvelope(UUID envelopeId) {
        return Mono.empty();
    }

    @Override
    public Mono<SignatureEnvelope> archiveEnvelope(UUID envelopeId) {
        return getEnvelope(envelopeId);
    }
}
