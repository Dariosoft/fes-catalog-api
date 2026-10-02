package com.friendlyeshop.catalog.model.dto;

import com.friendlyeshop.catalog.model.Currency;
import com.friendlyeshop.catalog.model.ProductStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID ownerAccountId,
        String name,
        BigDecimal price,
        Currency currency,
        Integer stock,
        ProductStage stage,
        List<ProductImageResponse> images,
        Instant createdAt,
        Instant updatedAt) {
}
