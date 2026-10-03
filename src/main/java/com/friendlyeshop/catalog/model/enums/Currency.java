package com.friendlyeshop.catalog.model.enums;

public enum Currency {
    ARS,
    USD;

    public static Currency from(String value) {
        for (Currency currency : values()) {
            if (value != null && currency.name().equalsIgnoreCase(value)) {
                return currency;
            }
        }
        throw new IllegalArgumentException("Unsupported currency: " + value);
    }
}
