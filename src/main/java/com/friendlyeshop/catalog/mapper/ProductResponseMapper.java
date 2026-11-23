package com.friendlyeshop.catalog.mapper;

import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.dto.ProductImageResponse;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.PublicationPageResponse;
import com.friendlyeshop.catalog.model.dto.PublicationResponse;
import com.friendlyeshop.catalog.util.UrlUtils;
import java.util.List;
import java.util.Map;
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
        return new ProductResponse(
                product.getId(),
                product.getOwnerAccountId(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                product.getStock(),
                product.getStage(),
                toImageResponses(images),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public PublicationResponse toPublication(Product product, List<ProductImage> images) {
        return new PublicationResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                product.getStock(),
                toImageResponses(images));
    }

    public PublicationPageResponse toPublicationPage(List<Product> products,
            Map<UUID, List<ProductImage>> imagesByProductId, int page, int size, long totalItems, int totalPages) {
        List<PublicationResponse> items = products.stream()
                .map(product -> toPublication(product,
                        imagesByProductId.getOrDefault(product.getId(), List.of())))
                .toList();
        return new PublicationPageResponse(items, page, size, totalItems, totalPages);
    }

    private List<ProductImageResponse> toImageResponses(List<ProductImage> images) {
        return images.stream()
                .map(this::toImageResponse)
                .toList();
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        return new ProductImageResponse(image.getId(), publicUrl(image.getId()));
    }

    private String publicUrl(UUID imageId) {
        return UrlUtils.join(properties.publicBaseUrl(), IMAGE_PATH + imageId);
    }
}
