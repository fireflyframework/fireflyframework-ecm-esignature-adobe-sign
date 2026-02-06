# Firefly ECM eSignature – Adobe Sign

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

Adobe Sign eSignature adapter for Firefly fireflyframework-ecm. Provides envelope lifecycle operations and integrates via the fireflyframework-ecm hexagonal ports.

## Features
- Envelope lifecycle: create, get, update, send, void, archive (basic mapping)
- OAuth refresh-token authentication and token caching
- Reactive WebClient with configurable timeouts and retries
- Spring Boot conditional auto-wiring via `firefly.ecm.esignature.provider=adobe-sign`
- Resilience hooks (circuit breaker/retry) ready for injection

## Installation
```xml
<dependency>
  <groupId>org.fireflyframework</groupId>
  <artifactId>fireflyframework-ecm-esignature-adobe-sign</artifactId>
  <version>${firefly.version}</version>
</dependency>
```

## Configuration
```yaml
firefly:
  ecm:
    enabled: true
    features:
      esignature: true
    esignature:
      provider: adobe-sign
    adapter:
      adobe-sign:
        client-id: ${ADOBE_SIGN_CLIENT_ID}
        client-secret: ${ADOBE_SIGN_CLIENT_SECRET}
        refresh-token: ${ADOBE_SIGN_REFRESH_TOKEN}
        # optional
        base-url: https://api.na1.adobesign.com
        api-version: v6
        connection-timeout: 30s
        read-timeout: 60s
        max-retries: 3
        token-expiration: 3600
        default-email-subject: "Please sign this document"
        default-email-message: "Please review and sign the attached document(s)."
        webhook-url: ${ADOBE_SIGN_WEBHOOK_URL:}
        webhook-secret: ${ADOBE_SIGN_WEBHOOK_SECRET:}
        enable-embedded-signing: false
        return-url: ${ADOBE_SIGN_RETURN_URL:}
        enable-document-retention: true
        document-retention-days: 365
        enable-reminders: true
        reminder-frequency-days: 3
```

Notes
- Embedded signing URL is currently not implemented; `getSigningUrl` throws `UnsupportedOperationException`.
- Map of documents/participants in requests is minimal; extend as needed in your service layer.

## Usage
```java
@Autowired SignatureEnvelopePort envelopePort;
SignatureEnvelope draft = SignatureEnvelope.builder()
    .title("NDA")
    .description("Please sign")
    .build();
Mono<SignatureEnvelope> created = envelopePort.createEnvelope(draft);
```

## Security
- Provide secrets via environment variables or a secret manager; avoid placing raw tokens in source control.

## Testing
- Includes a Spring Boot smoke test that verifies the adapter bean loads with the provider set to `adobe-sign`.

## License
Apache 2.0
