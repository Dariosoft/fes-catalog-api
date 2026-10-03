package com.friendlyeshop.catalog.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fes.catalog")
public record CatalogProperties(@NotBlank String publicBaseUrl) {
}
