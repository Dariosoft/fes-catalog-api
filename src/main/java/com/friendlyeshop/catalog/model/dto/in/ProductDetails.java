package com.friendlyeshop.catalog.model.dto.in;

import com.friendlyeshop.catalog.model.dto.request.ProductRequest;
import com.friendlyeshop.catalog.model.enums.Currency;
import java.math.BigDecimal;

public record ProductDetails(
        String name,
        String description,
        BigDecimal price,
        Currency currency,
        Integer stock) {
    public static ProductDetails from(ProductRequest request) {
        return new ProductDetails(request.name(), request.description(), request.price(), request.currency(),
                request.stock());
    }
}
