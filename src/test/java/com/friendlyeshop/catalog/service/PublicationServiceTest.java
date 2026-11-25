package com.friendlyeshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.exception.InvalidProductException;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.mapper.ProductResponseMapper;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.dto.request.PublicationRequest;
import com.friendlyeshop.catalog.model.dto.out.PublicationDetailResponse;
import com.friendlyeshop.catalog.model.dto.out.PublicationPageResponse;
import com.friendlyeshop.catalog.model.dto.out.PublicationResponse;
import com.friendlyeshop.catalog.model.dto.request.PublishCatalogRequest;
import com.friendlyeshop.catalog.model.dto.out.PublishCatalogResponse;
import com.friendlyeshop.catalog.model.dto.out.ProductResponse;
import com.friendlyeshop.catalog.model.dto.in.ProductDetails;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import com.friendlyeshop.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private PublicationService service;

    @BeforeEach
    void setUp() {
        service = new PublicationService(productRepository, productImageRepository,
                new ProductResponseMapper(new CatalogProperties("https://api.fes.test")),
                new ProductLookup(productRepository));
    }

    @Test
    void appliesDefaultPageAndSizeWhenParametersAreMissing() {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class))).thenReturn(Page.empty());

        PublicationPageResponse response = service.list(null, null, null);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(24);
        Pageable requested = capturedPageable();
        assertThat(requested.getPageNumber()).isZero();
        assertThat(requested.getPageSize()).isEqualTo(24);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "0", "-3", "3.5"})
    void fallsBackToDefaultPageForInvalidValues(String rawPage) {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class))).thenReturn(Page.empty());

        PublicationPageResponse response = service.list(null, rawPage, "10");

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(capturedPageable().getPageNumber()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "0", "-3"})
    void fallsBackToDefaultSizeForInvalidValues(String rawSize) {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class))).thenReturn(Page.empty());

        PublicationPageResponse response = service.list(null, null, rawSize);

        assertThat(response.size()).isEqualTo(24);
        assertThat(capturedPageable().getPageSize()).isEqualTo(24);
    }

    @Test
    void keepsValidPageAndSize() {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class))).thenReturn(Page.empty());

        PublicationPageResponse response = service.list(null, "3", "50");

        assertThat(response.page()).isEqualTo(3);
        assertThat(response.size()).isEqualTo(50);
        Pageable requested = capturedPageable();
        assertThat(requested.getPageNumber()).isEqualTo(2);
        assertThat(requested.getPageSize()).isEqualTo(50);
    }

    @Test
    void requestsFirstPageWithStableOrderAndReturnsTotals() {
        Product newest = publishedProduct("Recien publicado");
        Product older = publishedProduct("Mas antiguo");
        PageRequest firstPage = PageRequest.of(0, 24);
        when(productRepository.findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newest, older), firstPage, 30));
        when(productImageRepository.findByProductIdIn(List.of(newest.getId(), older.getId()))).thenReturn(List.of());

        PublicationPageResponse response = service.list(null, null, null);

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(24);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Order.desc("createdAt"), Order.asc("id")));
        assertThat(response.items()).extracting(PublicationResponse::name)
                .containsExactly("Recien publicado", "Mas antiguo");
        assertThat(response.totalItems()).isEqualTo(30);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.page()).isEqualTo(1);
    }

    @Test
    void doesNotInvokeAnyWriteOperation() {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 24), 0));

        service.list(null, null, null);

        verify(productRepository, never()).save(any(Product.class));
        verify(productRepository, never()).saveAll(any());
        verify(productRepository, never()).delete(any(Product.class));
        verify(productRepository, never()).deleteById(any(UUID.class));
        verify(productRepository, never()).deleteAll();
    }

    @Test
    void clampsRequestedPageToTheLastAvailablePage() {
        List<Product> lastPageProducts = List.of(publishedProduct("Uno"), publishedProduct("Dos"),
                publishedProduct("Tres"), publishedProduct("Cuatro"), publishedProduct("Cinco"),
                publishedProduct("Seis"));
        when(productRepository.findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable requested = invocation.getArgument(2);
                    return requested.getPageNumber() == 4
                            ? new PageImpl<Product>(List.of(), requested, 30)
                            : new PageImpl<>(lastPageProducts, requested, 30);
                });
        when(productImageRepository.findByProductIdIn(lastPageProducts.stream().map(Product::getId).toList()))
                .thenReturn(List.of());

        PublicationPageResponse response = service.list(null, "5", "24");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository, times(2)).findPublished(eq(ProductStage.PUBLISHED), eq(0), captor.capture());
        assertThat(captor.getAllValues()).extracting(Pageable::getPageNumber).containsExactly(4, 1);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(30);
        assertThat(response.items()).extracting(PublicationResponse::name).containsExactly("Uno", "Dos", "Tres",
                "Cuatro", "Cinco", "Seis");
    }

    @Test
    void returnsEmptyPageWithZeroTotals() {
        when(productRepository.findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(6, 10), 0));

        PublicationPageResponse response = service.list(" ", "7", "10");

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.page()).isEqualTo(7);
        assertThat(response.size()).isEqualTo(10);
        verifyNoInteractions(productImageRepository);
    }

    @Test
    void ignoresBlankNameAndUsesTheUnfilteredQuery() {
        when(productRepository.findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 24), 0));

        service.list("   ", null, null);

        verify(productRepository).findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class));
        verify(productRepository, never()).findPublishedByName(any(), anyInt(), any(), any());
    }

    @Test
    void sendsTheTrimmedNameToTheFilteredQuery() {
        when(productRepository.findPublishedByName(eq(ProductStage.PUBLISHED), eq(0), eq("Café"),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 24), 0));

        service.list("  Café  ", null, null);

        verify(productRepository).findPublishedByName(eq(ProductStage.PUBLISHED), eq(0), eq("Café"),
                any(Pageable.class));
        verify(productRepository, never()).findPublished(any(), anyInt(), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"101", "150"})
    void clipsSizeAboveTheMaximum(String rawSize) {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        PublicationPageResponse response = service.list(null, null, rawSize);

        assertThat(response.size()).isEqualTo(100);
        assertThat(capturedPageable().getPageSize()).isEqualTo(100);
    }

    @Test
    void roundsTotalPagesUpFromTotalItems() {
        Product product = publishedProduct("Unico en la primera pagina");
        when(productRepository.findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 24), 25));
        when(productImageRepository.findByProductIdIn(List.of(product.getId()))).thenReturn(List.of());

        PublicationPageResponse response = service.list(null, null, null);

        assertThat(response.totalItems()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.page()).isEqualTo(1);
    }

    @Test
    void groupsImagesByProductAndBuildsPublicUrls() {
        Product withImage = publishedProduct("Con imagen");
        Product withoutImage = publishedProduct("Sin imagen");
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.of(imageId, withImage.getId(), "products/a/b", "image/png");
        when(productRepository.findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withImage, withoutImage), PageRequest.of(0, 24), 2));
        when(productImageRepository.findByProductIdIn(List.of(withImage.getId(), withoutImage.getId())))
                .thenReturn(List.of(image));

        PublicationPageResponse response = service.list(null, null, null);

        assertThat(response.items().get(0).images()).singleElement().satisfies(publicImage -> {
            assertThat(publicImage.id()).isEqualTo(imageId);
            assertThat(publicImage.url()).isEqualTo("https://api.fes.test/catalog/images/" + imageId);
        });
        assertThat(response.items().get(1).images()).isEmpty();
    }

    private static Product publishedProduct(String name) {
        Product product = Product.openNew(UUID.randomUUID(),
                new ProductDetails(name, null, new BigDecimal("10.00"), Currency.ARS, 5));
        product.publish();
        return product;
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findPublished(eq(ProductStage.PUBLISHED), eq(0), captor.capture());
        return captor.getValue();
    }

    @Test
    void publishesValidDraft() {
        Product product = ownedProduct("Zapatillas", new BigDecimal("10.00"));
        stubFound(product);

        ProductResponse response = service.publish(product.getOwnerAccountId(), product.getId());

        assertThat(response.stage()).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void keepsPublicationWhenPublishingAgain() {
        Product product = ownedProduct("Zapatillas", new BigDecimal("10.00"));
        product.publish();
        stubFound(product);

        ProductResponse response = service.publish(product.getOwnerAccountId(), product.getId());

        assertThat(response.stage()).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void rejectsPublishingWithoutOwner() {
        Product product = Product.openNew(null,
                new ProductDetails("Zapatillas", null, new BigDecimal("10.00"), Currency.ARS, null));
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
        Product draft = Product.openNew(owner,
                new ProductDetails("Borrador", null, new BigDecimal("5.00"), Currency.ARS, null));
        when(productRepository.findByOwnerAccountIdAndStageAndDeletedAtIsNull(owner, ProductStage.DRAFT))
                .thenReturn(List.of(draft));
        lenient().when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PublishCatalogRequest request = new PublishCatalogRequest(
                List.of(new PublicationRequest("Nuevo", new BigDecimal("8.00"), Currency.USD, 3)));

        PublishCatalogResponse response = service.publishCatalog(owner, request);

        assertThat(response.published()).isEqualTo(2);
        assertThat(draft.getStage()).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void publishesZeroWhenThereIsNothingToPublish() {
        UUID owner = UUID.randomUUID();
        when(productRepository.findByOwnerAccountIdAndStageAndDeletedAtIsNull(owner, ProductStage.DRAFT))
                .thenReturn(List.of());

        PublishCatalogResponse response = service.publishCatalog(owner, new PublishCatalogRequest(List.of()));

        assertThat(response.published()).isZero();
    }

    @Test
    void rejectsPublishingForeignProduct() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(owner, id)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void returnsVisiblePublicationDetail() {
        Product product = publishedProduct("Café molido");
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.of(imageId, product.getId(), "products/a/b", "image/png");
        when(productRepository.findPublicById(product.getId(), ProductStage.PUBLISHED, 0))
                .thenReturn(Optional.of(product));
        when(productImageRepository.findByProductId(product.getId())).thenReturn(List.of(image));

        PublicationDetailResponse response = service.detail(product.getId());

        assertThat(response.id()).isEqualTo(product.getId());
        assertThat(response.name()).isEqualTo("Café molido");
        assertThat(response.price()).isEqualByComparingTo("10.00");
        assertThat(response.currency()).isEqualTo(Currency.ARS);
        assertThat(response.stock()).isEqualTo(5);
        assertThat(response.sellerAccountId()).isEqualTo(product.getOwnerAccountId());
        assertThat(response.images()).singleElement()
                .satisfies(publicImage -> assertThat(publicImage.url())
                        .isEqualTo("https://api.fes.test/catalog/images/" + imageId));
    }

    @Test
    void returnsNullDescriptionWhenAbsent() {
        Product product = publishedProduct("Zapatillas");
        when(productRepository.findPublicById(product.getId(), ProductStage.PUBLISHED, 0))
                .thenReturn(Optional.of(product));
        when(productImageRepository.findByProductId(product.getId())).thenReturn(List.of());

        PublicationDetailResponse response = service.detail(product.getId());

        assertThat(response.description()).isNull();
        assertThat(response.images()).isEmpty();
    }

    @Test
    void throwsNotFoundWhenPublicationDoesNotExistOrIsNotVisible() {
        UUID id = UUID.randomUUID();
        when(productRepository.findPublicById(id, ProductStage.PUBLISHED, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(id))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("La publicación no existe");
        verifyNoInteractions(productImageRepository);
    }

    @Test
    void detailDoesNotInvokeAnyWriteOperation() {
        Product product = publishedProduct("Zapatillas");
        when(productRepository.findPublicById(product.getId(), ProductStage.PUBLISHED, 0))
                .thenReturn(Optional.of(product));
        when(productImageRepository.findByProductId(product.getId())).thenReturn(List.of());

        service.detail(product.getId());

        verify(productRepository, never()).save(any(Product.class));
        verify(productRepository, never()).saveAll(any());
        verify(productRepository, never()).delete(any(Product.class));
        verify(productRepository, never()).deleteById(any(UUID.class));
        verify(productRepository, never()).deleteAll();
    }

    private Product ownedProduct(String name, BigDecimal price) {
        return Product.openNew(UUID.randomUUID(), new ProductDetails(name, null, price, Currency.ARS, null));
    }

    private void stubFound(Product product) {
        when(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(
                product.getId(), product.getOwnerAccountId())).thenReturn(Optional.of(product));
        lenient().when(productImageRepository.findByProductId(product.getId())).thenReturn(List.of());
    }
}
