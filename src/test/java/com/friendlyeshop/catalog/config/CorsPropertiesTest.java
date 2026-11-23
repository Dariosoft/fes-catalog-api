package com.friendlyeshop.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CorsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void startsWithoutOriginsWhenPropertyIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CorsProperties.class).browserOrigins()).isEmpty();
        });
    }

    @Test
    void startsWithoutOriginsWhenPropertyIsEmpty() {
        contextRunner.withPropertyValues("fes.cors.browser-origins=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CorsProperties.class).browserOrigins()).isEmpty();
                });
    }

    @Test
    void startsWithoutOriginsWhenPropertyOnlyHasSpaces() {
        contextRunner.withPropertyValues("fes.cors.browser-origins=   ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CorsProperties.class).browserOrigins()).isEmpty();
                });
    }

    @Test
    void bindsCommaSeparatedOriginsTrimmed() {
        contextRunner
                .withPropertyValues("fes.cors.browser-origins=https://shop.fes.test, https://admin.fes.test ")
                .run(context -> assertThat(context.getBean(CorsProperties.class).browserOrigins())
                        .containsExactly("https://shop.fes.test", "https://admin.fes.test"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class PropertiesConfiguration {
    }
}
