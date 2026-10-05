package com.friendlyeshop.catalog.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fes.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey) {
}
