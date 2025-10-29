package com.firefly.ecm.adapter.adobesign;

import com.firefly.core.ecm.port.esignature.SignatureEnvelopePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AdobeSignAdapterTest.TestConfig.class)
@TestPropertySource(properties = {
        "firefly.ecm.enabled=true",
        "firefly.ecm.features.esignature=true",
        "firefly.ecm.esignature.provider=adobe-sign",
        "firefly.ecm.adapter.adobe-sign.client-id=id",
        "firefly.ecm.adapter.adobe-sign.client-secret=sec",
        "firefly.ecm.adapter.adobe-sign.refresh-token=rt"
})
class AdobeSignAdapterTest {

    @Autowired
    private SignatureEnvelopePort envelopePort;

    @Test
    void contextLoads_andAdobeSignAdapterPresent() {
        assertThat(envelopePort).isInstanceOf(AdobeSignSignatureEnvelopeAdapter.class);
    }

    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.Import(AdobeSignSignatureEnvelopeAdapter.class)
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        org.springframework.web.reactive.function.client.WebClient adobeSignWebClient() {
            return org.springframework.web.reactive.function.client.WebClient.builder().baseUrl("https://api.example").build();
        }
        @org.springframework.context.annotation.Bean
        AdobeSignAdapterProperties properties() {
            AdobeSignAdapterProperties p = new AdobeSignAdapterProperties();
            p.setClientId("id");
            p.setClientSecret("sec");
            p.setRefreshToken("rt");
            return p;
        }
        @org.springframework.context.annotation.Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.findAndRegisterModules();
            return mapper;
        }
        
        @org.springframework.context.annotation.Bean(name = "adobeSignCircuitBreaker")
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker() {
            return io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("adobeSign");
        }
        @org.springframework.context.annotation.Bean(name = "adobeSignRetry")
        io.github.resilience4j.retry.Retry retry() {
            return io.github.resilience4j.retry.Retry.ofDefaults("adobeSign");
        }
        @org.springframework.context.annotation.Bean
        com.firefly.core.ecm.port.document.DocumentContentPort documentContentPort() {
            return org.mockito.Mockito.mock(com.firefly.core.ecm.port.document.DocumentContentPort.class);
        }
        @org.springframework.context.annotation.Bean
        com.firefly.core.ecm.port.document.DocumentPort documentPort() {
            return org.mockito.Mockito.mock(com.firefly.core.ecm.port.document.DocumentPort.class);
        }
    }
}
