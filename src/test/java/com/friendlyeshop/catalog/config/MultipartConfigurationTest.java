package com.friendlyeshop.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class MultipartConfigurationTest {

    @Test
    void allowsTenImagesOfTwoMegabytes() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yaml"));
        PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("2MB");
        assertThat(source.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("25MB");
    }
}
