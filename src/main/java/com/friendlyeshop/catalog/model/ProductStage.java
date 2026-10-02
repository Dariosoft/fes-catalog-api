package com.friendlyeshop.catalog.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductStage {
    DRAFT("draft"),
    PUBLISHED("published");

    private final String value;

    ProductStage(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static ProductStage from(String value) {
        for (ProductStage stage : values()) {
            if (value != null && stage.value.equalsIgnoreCase(value)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unsupported product stage: " + value);
    }
}
