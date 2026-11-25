package com.friendlyeshop.catalog.model.dto;

import com.friendlyeshop.catalog.model.enums.Currency;
import java.math.BigDecimal;

public record ProductDetails(
        String name,
        String description,
        BigDecimal price,
        Currency currency,
        Integer stock) {
    public static ProductDetails from(ProductForm form) {
        return new ProductDetails(form.name(), form.description(), form.price(), form.currency(), form.stock());
    }
}
