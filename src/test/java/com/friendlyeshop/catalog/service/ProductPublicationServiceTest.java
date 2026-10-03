package com.friendlyeshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.exception.InvalidProductException;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.mapper.ProductResponseMapper;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.PublishCatalogItem;
import com.friendlyeshop.catalog.model.dto.PublishCatalogRequest;
import com.friendlyeshop.catalog.model.dto.PublishCatalogResponse;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import com.friendlyeshop.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPublicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private ProductPublicationService service;

    @BeforeEach
    void setUp() {
        ProductResponseMapper mapper = new ProductResponseMapper(new CatalogProperties("https://api.fes.test"));
        service = new ProductPublicationService(productRepository, productImageRepository, mapper,
                new ProductLookup(productRepository));
    }

    @Test
    void publishesValidDraft() {
        Product product = ownedProduct("Zapatillas", new BigDecimal("10.00"));
        stubFound(product);

        ProductResponse response = service.publish(product.getOwnerAccountId(), product.getId());

        assertThat(response.stage()).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void keepsPublishedProductWhenPublishingAgain() {
        Product product = ownedProduct("Zapatillas", new BigDecimal("10.00"));
        product.publish();
        stubFound(product);

        ProductResponse response = service.publish(product.getOwnerAccountId(), product.getId());

        assertThat(response.stage()).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void rejectsPublishingWithoutOwner() {
        Product product = Product.openNew(null, "Zapatillas", new BigDecimal("10.00"), Currency.ARS, null);
        stubFound(product);

        assertThatThrownBy(() -> service.publish(product.getOwnerAccountId(), product.getId()))
                .isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void rejectsPublishingWithoutName() {
        Product product = ownedProduct("  ", new BigDecimal("10.00"));
        stubFound(product);

        assertThatThrownBy(() -> service.publish(product.getOwnerAccountId(), product.getId()))
                .isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void rejectsPublishingWithoutPrice() {
        Product product = ownedProduct("Zapatillas", null);
        stubFound(product);

        assertThatThrownBy(() -> service.publish(product.getOwnerAccountId(), product.getId()))
                .isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void unpublishesKeepingOwner() {
        Product product = ownedProduct("Zapatillas", new BigDecimal("10.00"));
        product.publish();
        stubFound(product);

        ProductResponse response = service.unpublish(product.getOwnerAccountId(), product.getId());

        assertThat(response.stage()).isEqualTo(ProductStage.DRAFT);
        assertThat(response.ownerAccountId()).isEqualTo(product.getOwnerAccountId());
    }

    @Test
    void publishesOwnerlessItemsAndAccountDrafts() {
        UUID owner = UUID.randomUUID();
        Product draft = Product.openNew(owner, "Borrador", new BigDecimal("5.00"), Currency.ARS, null);
        when(productRepository.findByOwnerAccountIdAndStageAndDeletedAtIsNull(owner, ProductStage.DRAFT))
                .thenReturn(List.of(draft));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PublishCatalogRequest request = new PublishCatalogRequest(
                List.of(new PublishCatalogItem("Nuevo", new BigDecimal("8.00"), Currency.USD, 3)));

        PublishCatalogResponse response = service.publishCatalog(owner, request);

        assertThat(response.published()).isEqualTo(2);
        assertThat(draft.getStage()).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void publishesZeroWhenThereIsNothingToPublish() {
        UUID owner = UUID.randomUUID();
        when(productRepository.findByOwnerAccountIdAndStageAndDeletedAtIsNull(owner, ProductStage.DRAFT))
                .thenReturn(List.of());

        PublishCatalogResponse response = service.publishCatalog(owner,
                new PublishCatalogRequest(List.of()));

        assertThat(response.published()).isZero();
    }

    @Test
    void rejectsPublishingForeignProduct() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(owner, id)).isInstanceOf(ProductNotFoundException.class);
    }

    private Product ownedProduct(String name, BigDecimal price) {
        return Product.openNew(UUID.randomUUID(), name, price, Currency.ARS, null);
    }

    private void stubFound(Product product) {
        when(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(
                product.getId(), product.getOwnerAccountId())).thenReturn(Optional.of(product));
        lenient().when(productImageRepository.findByProductId(product.getId())).thenReturn(List.of());
    }
}
