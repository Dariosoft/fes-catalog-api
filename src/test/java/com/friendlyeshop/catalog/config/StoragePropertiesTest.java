package com.friendlyeshop.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class StoragePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsPropertiesFromEnvironment() {
        contextRunner
                .withPropertyValues(
                        "fes.storage.endpoint=http://localhost:9000",
                        "fes.storage.bucket=product-images",
                        "fes.storage.access-key=key",
                        "fes.storage.secret-key=secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(StorageProperties.class).bucket()).isEqualTo("product-images");
                });
    }

    @Test
    void failsWhenRequiredPropertyIsMissing() {
        contextRunner
                .withPropertyValues(
                        "fes.storage.endpoint=http://localhost:9000",
                        "fes.storage.bucket=product-images",
                        "fes.storage.access-key=key")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StorageProperties.class)
    static class PropertiesConfiguration {
    }
}
