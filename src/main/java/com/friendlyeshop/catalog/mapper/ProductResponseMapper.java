package com.friendlyeshop.catalog.mapper;

import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.dto.out.ProductImageResponse;
import com.friendlyeshop.catalog.model.dto.out.ProductResponse;
import com.friendlyeshop.catalog.model.dto.out.PublicationDetailResponse;
import com.friendlyeshop.catalog.model.dto.out.PublicationPageResponse;
import com.friendlyeshop.catalog.model.dto.out.PublicationResponse;
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
        return ProductResponse.from(product, toImageResponses(images));
    }

    public PublicationResponse toPublication(Product product, List<ProductImage> images) {
        return PublicationResponse.from(product, toImageResponses(images));
    }

    public PublicationDetailResponse toPublicationDetail(Product product, List<ProductImage> images) {
        return PublicationDetailResponse.from(product, toImageResponses(images));
    }

    public PublicationPageResponse toPublicationPage(List<Product> products,
            Map<UUID, List<ProductImage>> imagesByProductId, int page, int size, long totalItems, int totalPages) {
        List<PublicationResponse> items = products.stream()
                .map(product -> toPublication(product,
                        imagesByProductId.getOrDefault(product.getId(), List.of())))
                .toList();
        return PublicationPageResponse.from(items, page, size, totalItems, totalPages);
    }

    private List<ProductImageResponse> toImageResponses(List<ProductImage> images) {
        return images.stream()
                .map(this::toImageResponse)
                .toList();
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        return ProductImageResponse.from(image, publicUrl(image.getId()));
    }

    private String publicUrl(UUID imageId) {
        return UrlUtils.join(properties.publicBaseUrl(), IMAGE_PATH + imageId);
    }
}
