package com.friendlyeshop.catalog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Publishes every controller below {@code /catalog} so each one only declares its own resource path.
 * Actuator endpoints are registered by their own handler mapping and keep their paths outside the
 * prefix. A future controller that must live outside {@code /catalog} belongs in a different package.
 */
@Configuration
public class ApiPathConfig implements WebMvcConfigurer {

    static final String API_BASE_PATH = "/catalog";

    private static final HandlerTypePredicate CONTROLLERS =
            HandlerTypePredicate.forBasePackage("com.friendlyeshop.catalog.controller");

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_BASE_PATH, CONTROLLERS);
    }
}
