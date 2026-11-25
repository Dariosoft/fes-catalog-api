package com.friendlyeshop.catalog.model.dto.out;

import com.friendlyeshop.catalog.model.ProductImage;
import java.util.UUID;

public record ProductImageResponse(UUID id, String url) {
    public static ProductImageResponse from(ProductImage image, String url) {
        return new ProductImageResponse(image.getId(), url);
    }
}
