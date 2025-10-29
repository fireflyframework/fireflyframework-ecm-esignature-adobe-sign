/*
 * Copyright (c) 2024 Firefly Software Solutions Inc.
 */
package com.firefly.ecm.adapter.adobesign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "firefly.ecm.adapter.adobe-sign")
public class AdobeSignAdapterProperties {

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String refreshToken;

    private String baseUrl = "https://api.na1.adobesign.com";

    private String apiVersion = "v6";

    private String webhookUrl;

    private String webhookSecret;

    private Duration connectionTimeout = Duration.ofSeconds(30);

    private Duration readTimeout = Duration.ofSeconds(60);

    @Min(0)
    @Max(10)
    private Integer maxRetries = 3;

    @Min(300)
    @Max(86400)
    private Integer tokenExpiration = 3600;

    private String defaultEmailSubject = "Please sign this document";

    private String defaultEmailMessage = "Please review and sign the attached document(s).";

    private Boolean enableEmbeddedSigning = false;

    private String returnUrl;

    private Boolean enableDocumentRetention = true;

    @Min(1)
    @Max(3650)
    private Integer documentRetentionDays = 365;

    private Boolean enableReminders = true;

    @Min(1)
    @Max(30)
    private Integer reminderFrequencyDays = 3;
}
