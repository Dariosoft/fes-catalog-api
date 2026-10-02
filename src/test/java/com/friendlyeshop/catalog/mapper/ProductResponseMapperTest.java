package com.friendlyeshop.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.model.Currency;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.ProductStage;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductResponseMapperTest {

    private static final UUID OWNER = UUID.randomUUID();

    private final ProductResponseMapper mapper =
        new ProductResponseMapper(new CatalogProperties("https://api.fes.test/"));

    @Test
    void buildsPublicImageUrl() {
        Product product = Product.openNew(OWNER, "Zapatillas", new BigDecimal("10.00"), Currency.ARS, 4);
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.of(imageId, product.getId(), "products/a/b", "image/png");

        ProductResponse response = mapper.toResponse(product, List.of(image));

        assertThat(response.images()).hasSize(1);
        assertThat(response.images().get(0).id()).isEqualTo(imageId);
        assertThat(response.images().get(0).url())
                .isEqualTo("https://api.fes.test/catalog/images/" + imageId);
    }

    @Test
    void mapsProductWithoutImages() {
        Product product = Product.openNew(OWNER, "Zapatillas", new BigDecimal("10.00"), Currency.USD, null);

        ProductResponse response = mapper.toResponse(product, List.of());

        assertThat(response.stage()).isEqualTo(ProductStage.DRAFT);
        assertThat(response.ownerAccountId()).isEqualTo(OWNER);
        assertThat(response.images()).isEmpty();
    }
}
