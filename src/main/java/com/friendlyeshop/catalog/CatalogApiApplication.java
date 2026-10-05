package com.friendlyeshop.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CatalogApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogApiApplication.class, args);
    }
}
