# Firefly Framework - ECM eSignature - Adobe Sign

[![CI](https://github.com/fireflyframework/fireflyframework-ecm-esignature-adobe-sign/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-ecm-esignature-adobe-sign/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Adobe Sign eSignature adapter for Firefly ECM.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

This module implements the Firefly ECM e-signature ports using Adobe Sign as the provider. It provides `AdobeSignSignatureEnvelopeAdapter` which integrates with Adobe Sign's REST API for creating signature envelopes, managing signature requests, and tracking signing status.

The adapter auto-configures via `AdobeSignAutoConfiguration` and is activated by including this module on the classpath alongside the ECM core module.

## Features

- Adobe Sign integration for e-signature envelope management
- Spring Boot auto-configuration for seamless activation
- Implements Firefly ECM SignatureEnvelopePort
- Configurable via application properties
- Standalone provider library (include alongside fireflyframework-ecm)

## Requirements

- Java 21+
- Spring Boot 3.x
- Maven 3.9+
- Adobe Sign account and API credentials

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-adobe-sign</artifactId>
    <version>26.02.02</version>
</dependency>
```

## Quick Start

The adapter is automatically activated when included on the classpath with the ECM core module:

```xml
<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-ecm</artifactId>
    </dependency>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-ecm-esignature-adobe-sign</artifactId>
    </dependency>
</dependencies>
```

## Configuration

```yaml
firefly:
  ecm:
    esignature:
      adobe-sign:
        api-access-point: https://api.adobesign.com
        integration-key: your-integration-key
```

## Documentation

No additional documentation available for this project.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
