package com.friendlyeshop.catalog.model.dto.out;

import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.enums.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicationDetailResponse(
        UUID id,
        String name,
        BigDecimal price,
        Currency currency,
        Integer stock,
        String description,
        UUID sellerAccountId,
        List<ProductImageResponse> images) {
    public static PublicationDetailResponse from(Product product, List<ProductImageResponse> images) {
        return new PublicationDetailResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                product.getStock(),
                product.getDescription(),
                product.getOwnerAccountId(),
                images);
    }
}
