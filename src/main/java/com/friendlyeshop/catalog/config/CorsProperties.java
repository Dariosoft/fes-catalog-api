package com.friendlyeshop.catalog.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fes.cors")
public record CorsProperties(List<String> browserOrigins) {

    public CorsProperties {
        browserOrigins = browserOrigins == null
                ? List.of()
                : browserOrigins.stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList();
    }
}
