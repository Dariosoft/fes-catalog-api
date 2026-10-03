package com.friendlyeshop.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ActuatorConfigurationTest {

    @Test
    void exposesHealthInfoAndPrometheus() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yaml"));
        PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("management.endpoints.web.exposure.include").toString())
                .contains("health", "info", "prometheus");
    }
}
