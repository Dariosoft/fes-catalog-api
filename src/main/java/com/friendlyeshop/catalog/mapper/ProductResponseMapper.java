package com.friendlyeshop.catalog.mapper;

import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.dto.ProductImageResponse;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.util.UrlUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProductResponseMapper {

    private static final String IMAGE_PATH = "/catalog/images/";

    private final CatalogProperties properties;

    public ProductResponseMapper(CatalogProperties properties) {
        this.properties = properties;
    }

    public ProductResponse toResponse(Product product, List<ProductImage> images) {
        List<ProductImageResponse> imageResponses = images.stream()
                .map(this::toImageResponse)
                .toList();
        return new ProductResponse(
                product.getId(),
                product.getOwnerAccountId(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                product.getStock(),
                product.getStage(),
                imageResponses,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        return new ProductImageResponse(image.getId(), publicUrl(image.getId()));
    }

    private String publicUrl(UUID imageId) {
        return UrlUtils.join(properties.publicBaseUrl(), IMAGE_PATH + imageId);
    }
}
