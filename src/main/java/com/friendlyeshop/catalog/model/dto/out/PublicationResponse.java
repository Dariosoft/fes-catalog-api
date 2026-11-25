package com.friendlyeshop.catalog.model.dto.out;

import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.enums.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicationResponse(
        UUID id,
        String name,
        BigDecimal price,
        Currency currency,
        Integer stock,
        List<ProductImageResponse> images) {
    public static PublicationResponse from(Product product, List<ProductImageResponse> images) {
        return new PublicationResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                product.getStock(),
                images);
    }
}
