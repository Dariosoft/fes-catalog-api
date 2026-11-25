package com.friendlyeshop.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.model.dto.ProductDetails;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.PublicationDetailResponse;
import com.friendlyeshop.catalog.model.dto.PublicationPageResponse;
import com.friendlyeshop.catalog.model.dto.PublicationResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductResponseMapperTest {

    private static final UUID OWNER = UUID.randomUUID();

    private final ProductResponseMapper mapper =
        new ProductResponseMapper(new CatalogProperties("https://api.fes.test/"));

    @Test
    void buildsPublicImageUrl() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", null, new BigDecimal("10.00"), Currency.ARS, 4));
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
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", null, new BigDecimal("10.00"), Currency.USD, null));

        ProductResponse response = mapper.toResponse(product, List.of());

        assertThat(response.stage()).isEqualTo(ProductStage.DRAFT);
        assertThat(response.ownerAccountId()).isEqualTo(OWNER);
        assertThat(response.images()).isEmpty();
    }

    @Test
    void mapsPublicationWithPublicImageUrlsOnly() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", null, new BigDecimal("10.00"), Currency.USD, 4));
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.of(imageId, product.getId(), "products/a/b", "image/png");

        PublicationResponse response = mapper.toPublication(product, List.of(image));

        assertThat(response.id()).isEqualTo(product.getId());
        assertThat(response.name()).isEqualTo("Zapatillas");
        assertThat(response.price()).isEqualByComparingTo("10.00");
        assertThat(response.currency()).isEqualTo(Currency.USD);
        assertThat(response.stock()).isEqualTo(4);
        assertThat(response.images()).singleElement().satisfies(publicImage -> {
            assertThat(publicImage.id()).isEqualTo(imageId);
            assertThat(publicImage.url()).isEqualTo("https://api.fes.test/catalog/images/" + imageId);
        });
    }

    @Test
    void mapsPublicationWithoutImagesToEmptyList() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", null, new BigDecimal("10.00"), Currency.ARS, 1));

        PublicationResponse response = mapper.toPublication(product, List.of());

        assertThat(response.images()).isEmpty();
    }

    @Test
    void composesPublishedPageKeepingOrderAndTotals() {
        Product first = Product.openNew(OWNER,
                new ProductDetails("Primero", null, new BigDecimal("1.00"), Currency.ARS, 1));
        Product second = Product.openNew(OWNER,
                new ProductDetails("Segundo", null, new BigDecimal("2.00"), Currency.ARS, 2));
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.of(imageId, second.getId(), "products/s/1", "image/png");

        PublicationPageResponse page = mapper.toPublicationPage(List.of(second, first),
                Map.of(second.getId(), List.of(image)), 2, 24, 30, 2);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(24);
        assertThat(page.totalItems()).isEqualTo(30);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).extracting(PublicationResponse::name).containsExactly("Segundo", "Primero");
        assertThat(page.items().get(0).images()).hasSize(1);
        assertThat(page.items().get(1).images()).isEmpty();
    }

    @Test
    void composesEmptyPublishedPageWithZeroTotals() {
        PublicationPageResponse page = mapper.toPublicationPage(List.of(), Map.of(), 5, 24, 0, 0);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.page()).isEqualTo(5);
    }

    @Test
    void includesDescriptionInPanelResponse() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", "Café de tueste medio", new BigDecimal("10.00"), Currency.ARS, 4));

        ProductResponse response = mapper.toResponse(product, List.of());

        assertThat(response.description()).isEqualTo("Café de tueste medio");
        assertThat(response.ownerAccountId()).isEqualTo(OWNER);
        assertThat(response.stage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void mapsNullDescriptionInPanelResponse() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", null, new BigDecimal("10.00"), Currency.ARS, 4));

        ProductResponse response = mapper.toResponse(product, List.of());

        assertThat(response.description()).isNull();
    }

    @Test
    void buildsPublicationDetailWithPublicSellerAndImages() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Café molido", "Café de tueste medio", new BigDecimal("1234.50"), Currency.ARS,
                        7));
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.of(imageId, product.getId(), "products/a/b", "image/png");

        PublicationDetailResponse response = mapper.toPublicationDetail(product, List.of(image));

        assertThat(response.id()).isEqualTo(product.getId());
        assertThat(response.name()).isEqualTo("Café molido");
        assertThat(response.price()).isEqualByComparingTo("1234.50");
        assertThat(response.currency()).isEqualTo(Currency.ARS);
        assertThat(response.stock()).isEqualTo(7);
        assertThat(response.description()).isEqualTo("Café de tueste medio");
        assertThat(response.sellerAccountId()).isEqualTo(OWNER);
        assertThat(response.images()).singleElement().satisfies(publicImage -> {
            assertThat(publicImage.id()).isEqualTo(imageId);
            assertThat(publicImage.url()).isEqualTo("https://api.fes.test/catalog/images/" + imageId);
        });
    }

    @Test
    void mapsPublicationDetailWithoutImagesToEmptyListAndNullDescription() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", null, new BigDecimal("10.00"), Currency.USD, null));

        PublicationDetailResponse response = mapper.toPublicationDetail(product, List.of());

        assertThat(response.images()).isEmpty();
        assertThat(response.description()).isNull();
        assertThat(response.stock()).isNull();
    }
}
