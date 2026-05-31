# Firefly Framework - ECM eSignature Adobe Sign

[![CI](https://github.com/fireflyframework/fireflyframework-ecm-esignature-adobe-sign/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-ecm-esignature-adobe-sign/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Adobe Sign eSignature adapter for the Firefly Framework ECM abstraction — a pluggable, reactive provider that fulfills the ECM `SignatureEnvelopePort` SPI by driving the Adobe Sign (Acrobat Sign) REST API.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-ecm-esignature-adobe-sign` is one of the pluggable eSignature **provider adapters** for the Firefly Framework Enterprise Content Management (ECM) abstraction. The ECM core module (`fireflyframework-ecm`) defines a set of provider-neutral hexagonal **ports** — including `SignatureEnvelopePort` for electronic-signature envelope/agreement lifecycle management — and this module supplies a concrete, fully reactive implementation backed by [Adobe Sign / Adobe Acrobat Sign](https://www.adobe.com/sign.html).

Application code depends only on the ECM port interfaces, never on Adobe Sign types. To use Adobe Sign as the signing backend you add this adapter to the classpath and select it with a single property — `firefly.ecm.esignature.provider=adobe-sign` — so you can swap signing providers without touching business logic. This module is a drop-in sibling of the other ECM eSignature adapters (`fireflyframework-ecm-esignature-docusign`, `fireflyframework-ecm-esignature-logalty`); choosing a provider is purely a matter of which adapter is on the classpath plus the `provider` property.

The adapter is non-blocking from end to end: it is built on Spring WebFlux `WebClient` and Project Reactor (`Mono`/`Flux`), and it wraps every Adobe Sign call with Resilience4j circuit breaker and retry decorators. OAuth access tokens are obtained from a long-lived refresh token and cached/refreshed transparently. Auto-configuration (`AdobeSignAutoConfiguration`) wires everything from a handful of properties, so in most cases no Java configuration is required.

### Where it sits in the framework

| Layer | Artifact | Responsibility |
| --- | --- | --- |
| Core SPI | `fireflyframework-ecm` | Defines `SignatureEnvelopePort`, `DocumentPort`, `DocumentContentPort`, domain models and enums |
| Adapter (this module) | `fireflyframework-ecm-esignature-adobe-sign` | Implements `SignatureEnvelopePort` against the Adobe Sign REST API |
| Sibling adapters | `…-esignature-docusign`, `…-esignature-logalty` | Alternative eSignature providers selected via the same `provider` property |

## Features

- **Implements the ECM `SignatureEnvelopePort` SPI** — `AdobeSignSignatureEnvelopeAdapter` maps the framework's signature-envelope lifecycle (create, get, send, void, archive, sync status, lookup by external id) onto Adobe Sign *agreements*.
- **Fully reactive, non-blocking I/O** — built on Spring WebFlux `WebClient` and Project Reactor; all operations return `Mono`/`Flux`.
- **Annotated as a discoverable ECM adapter** — `@EcmAdapter(type = "adobe-sign", …)` declares supported features (`ESIGNATURE_ENVELOPES`, `ESIGNATURE_REQUESTS`, `SIGNATURE_VALIDATION`) plus required/optional properties for framework introspection.
- **Automatic OAuth token management** — exchanges a configured refresh token for short-lived access tokens against Adobe Sign's `/oauth/token` endpoint and refreshes them transparently before expiry.
- **Built-in resilience** — every remote call runs through dedicated Resilience4j `CircuitBreaker` (`adobeSign`, 50% failure threshold, 10-call sliding window, 30s open state) and `Retry` (configurable `max-retries`) operators.
- **Spring Boot auto-configuration** — `AdobeSignAutoConfiguration` is registered via `AutoConfiguration.imports`; the dedicated `WebClient`, circuit breaker, retry and adapter beans are all `@ConditionalOnMissingBean`, so any of them can be overridden.
- **Conditional activation** — beans only materialise when `firefly.ecm.esignature.provider=adobe-sign`, keeping the adapter inert when another provider is selected.
- **Validated, typed configuration** — `AdobeSignAdapterProperties` (`firefly.ecm.adapter.adobe-sign.*`) uses Jakarta Bean Validation (`@NotBlank`, `@Min`/`@Max`) with sensible defaults for API version, timeouts, retention and reminder behaviour.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- The `fireflyframework-ecm` core module on the classpath (pulled in transitively by this adapter)
- An Adobe Sign / Adobe Acrobat Sign account with API access and OAuth credentials (`client-id`, `client-secret`, `refresh-token`)

## Installation

Add the adapter alongside the ECM core. The version is managed by the Firefly BOM / parent, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-adobe-sign</artifactId>
    <!-- version managed by the Firefly BOM / fireflyframework-parent -->
</dependency>
```

The ECM core (`fireflyframework-ecm`) is a transitive dependency of this adapter, so you do not need to declare it explicitly. If you manage versions yourself, inherit from `fireflyframework-parent` or import the Firefly BOM in `<dependencyManagement>`.

## Quick Start

1. **Add the dependency** (see [Installation](#installation)).

2. **Select Adobe Sign as the eSignature provider** and supply credentials in `application.yml`:

```yaml
firefly:
  ecm:
    features:
      esignature: true
    esignature:
      provider: adobe-sign
    adapter:
      adobe-sign:
        client-id: ${ADOBE_SIGN_CLIENT_ID}
        client-secret: ${ADOBE_SIGN_CLIENT_SECRET}
        refresh-token: ${ADOBE_SIGN_REFRESH_TOKEN}
        base-url: https://api.na1.adobesign.com
```

3. **Inject the port and use it** — your code depends on the framework SPI, not on Adobe Sign:

```java
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ContractService {

    private final SignatureEnvelopePort envelopes; // AdobeSignSignatureEnvelopeAdapter injected by auto-config

    public ContractService(SignatureEnvelopePort envelopes) {
        this.envelopes = envelopes;
    }

    public Mono<SignatureEnvelope> startSigning(SignatureEnvelope envelope) {
        return envelopes.createEnvelope(envelope)        // creates the Adobe Sign agreement
            .flatMap(created -> envelopes.sendEnvelope(created.getId(), created.getCreatedBy()));
    }
}
```

When `firefly.ecm.esignature.provider` is `adobe-sign`, `AdobeSignAutoConfiguration` registers the `WebClient`, circuit breaker, retry and `AdobeSignSignatureEnvelopeAdapter` beans automatically and exposes the adapter as the active `SignatureEnvelopePort`.

## Configuration

All properties live under the `firefly.ecm.adapter.adobe-sign` prefix (bound by `AdobeSignAdapterProperties`). The provider selector `firefly.ecm.esignature.provider` lives under `firefly.ecm.esignature`.

```yaml
firefly:
  ecm:
    esignature:
      provider: adobe-sign           # selects this adapter (required to activate)
    adapter:
      adobe-sign:
        client-id: ${ADOBE_SIGN_CLIENT_ID}          # required
        client-secret: ${ADOBE_SIGN_CLIENT_SECRET}  # required
        refresh-token: ${ADOBE_SIGN_REFRESH_TOKEN}  # required
        base-url: https://api.na1.adobesign.com     # Adobe Sign API access point (region-specific)
        api-version: v6                             # Adobe Sign REST API version
        webhook-url:                                # optional callback URL for status events
        webhook-secret:                             # optional shared secret to verify webhooks
        connection-timeout: 30s                     # HTTP connection timeout
        read-timeout: 60s                           # HTTP read timeout
        max-retries: 3                              # Resilience4j retry attempts (0-10)
        token-expiration: 3600                      # access-token lifetime hint, seconds (300-86400)
        default-email-subject: "Please sign this document"
        default-email-message: "Please review and sign the attached document(s)."
        enable-embedded-signing: false              # embedded (in-app) signing flow
        return-url:                                 # redirect URL after embedded signing
        enable-document-retention: true             # retain signed documents in Adobe Sign
        document-retention-days: 365                # retention window in days (1-3650)
        enable-reminders: true                      # send signing reminders to recipients
        reminder-frequency-days: 3                  # reminder cadence in days (1-30)
```

### Key properties

| Property | Default | Description |
| --- | --- | --- |
| `firefly.ecm.esignature.provider` | _(none)_ | Must equal `adobe-sign` to activate this adapter. |
| `client-id` | _(required)_ | Adobe Sign OAuth client (application) id. |
| `client-secret` | _(required)_ | Adobe Sign OAuth client secret. |
| `refresh-token` | _(required)_ | Long-lived OAuth refresh token used to mint access tokens. |
| `base-url` | `https://api.na1.adobesign.com` | Region-specific Adobe Sign API access point. |
| `api-version` | `v6` | Adobe Sign REST API version used in request paths. |
| `connection-timeout` | `30s` | WebClient connection timeout (`java.time.Duration`). |
| `read-timeout` | `60s` | WebClient read timeout (`java.time.Duration`). |
| `max-retries` | `3` | Retry attempts on transient failures (validated `0`–`10`). |
| `token-expiration` | `3600` | Access-token lifetime hint in seconds (validated `300`–`86400`). |
| `enable-embedded-signing` | `false` | Enables the embedded signing flow (`return-url` used for redirect). |
| `enable-document-retention` / `document-retention-days` | `true` / `365` | Signed-document retention policy (days validated `1`–`3650`). |
| `enable-reminders` / `reminder-frequency-days` | `true` / `3` | Recipient reminder policy (cadence validated `1`–`30`). |

A ready-to-import profile is bundled at `src/main/resources/application-adobe-sign.yml`; activate it with the Spring profile `adobe-sign` to apply the baseline configuration.

## How It Works

`AdobeSignSignatureEnvelopeAdapter` translates the provider-neutral `SignatureEnvelope` domain model into Adobe Sign *agreements*:

- **Authentication** — `ensureValidAccessToken()` exchanges the configured `refresh-token` for an access token via Adobe Sign's `/oauth/token` endpoint, caching it until shortly before expiry.
- **Create** — `createEnvelope(...)` `POST`s an agreement to `/api/rest/{apiVersion}/agreements`, records the bidirectional mapping between the framework envelope `UUID` and the Adobe Sign agreement id, and returns the envelope in `DRAFT` status with provider `ADOBE_SIGN`.
- **Read & sync** — `getEnvelope(...)`, `getEnvelopeByExternalId(...)` and `syncEnvelopeStatus(...)` `GET` the agreement and map Adobe Sign status back onto `EnvelopeStatus`.
- **Resilience** — each remote pipeline is wrapped with `CircuitBreakerOperator` and `RetryOperator` so transient Adobe Sign failures are retried and sustained failures trip the breaker.

> Note: this adapter currently focuses on the agreement create/read/status path. Some `SignatureEnvelopePort` operations (e.g. embedded signing URL generation, status-filtered listings) are intentionally minimal or return empty results in the current implementation; contributions extending coverage are welcome.

## Documentation

- Firefly Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- ECM core SPI and domain model: [`fireflyframework-ecm`](https://github.com/fireflyframework/fireflyframework-ecm)
- Sibling eSignature adapters: [`…-esignature-docusign`](https://github.com/fireflyframework/fireflyframework-ecm-esignature-docusign), [`…-esignature-logalty`](https://github.com/fireflyframework/fireflyframework-ecm-esignature-logalty)
- Adobe Sign / Acrobat Sign REST API reference: [Adobe Developer](https://developer.adobe.com/document-services/docs/overview/adobe-sign/)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
