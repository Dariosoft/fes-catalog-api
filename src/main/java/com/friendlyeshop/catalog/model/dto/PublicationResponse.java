package com.friendlyeshop.catalog.model.dto;

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
}
