package com.friendlyeshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.mapper.ProductResponseMapper;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.dto.PublishedProductPageResponse;
import com.friendlyeshop.catalog.model.dto.PublishedProductResponse;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import com.friendlyeshop.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
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
class PublishedProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private PublishedProductService service;

    @BeforeEach
    void setUp() {
        service = new PublishedProductService(productRepository, productImageRepository,
                new ProductResponseMapper(new CatalogProperties("https://api.fes.test")));
    }

    @Test
    void appliesDefaultPageAndSizeWhenParametersAreMissing() {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class))).thenReturn(Page.empty());

        PublishedProductPageResponse response = service.list(null, null, null);

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

        PublishedProductPageResponse response = service.list(null, rawPage, "10");

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(capturedPageable().getPageNumber()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "0", "-3"})
    void fallsBackToDefaultSizeForInvalidValues(String rawSize) {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class))).thenReturn(Page.empty());

        PublishedProductPageResponse response = service.list(null, null, rawSize);

        assertThat(response.size()).isEqualTo(24);
        assertThat(capturedPageable().getPageSize()).isEqualTo(24);
    }

    @Test
    void keepsValidPageAndSize() {
        when(productRepository.findPublished(any(), anyInt(), any(Pageable.class))).thenReturn(Page.empty());

        PublishedProductPageResponse response = service.list(null, "3", "50");

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

        PublishedProductPageResponse response = service.list(null, null, null);

        Pageable pageable = capturedPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(24);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Order.desc("createdAt"), Order.asc("id")));
        assertThat(response.items()).extracting(PublishedProductResponse::name)
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

        PublishedProductPageResponse response = service.list(null, "5", "24");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository, times(2)).findPublished(eq(ProductStage.PUBLISHED), eq(0), captor.capture());
        assertThat(captor.getAllValues()).extracting(Pageable::getPageNumber).containsExactly(4, 1);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(30);
        assertThat(response.items()).extracting(PublishedProductResponse::name).containsExactly("Uno", "Dos", "Tres",
                "Cuatro", "Cinco", "Seis");
    }

    @Test
    void returnsEmptyPageWithZeroTotals() {
        when(productRepository.findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(6, 10), 0));

        PublishedProductPageResponse response = service.list(" ", "7", "10");

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

        PublishedProductPageResponse response = service.list(null, null, rawSize);

        assertThat(response.size()).isEqualTo(100);
        assertThat(capturedPageable().getPageSize()).isEqualTo(100);
    }

    @Test
    void roundsTotalPagesUpFromTotalItems() {
        Product product = publishedProduct("Unico en la primera pagina");
        when(productRepository.findPublished(eq(ProductStage.PUBLISHED), eq(0), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 24), 25));
        when(productImageRepository.findByProductIdIn(List.of(product.getId()))).thenReturn(List.of());

        PublishedProductPageResponse response = service.list(null, null, null);

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

        PublishedProductPageResponse response = service.list(null, null, null);

        assertThat(response.items().get(0).images()).singleElement().satisfies(publicImage -> {
            assertThat(publicImage.id()).isEqualTo(imageId);
            assertThat(publicImage.url()).isEqualTo("https://api.fes.test/catalog/images/" + imageId);
        });
        assertThat(response.items().get(1).images()).isEmpty();
    }

    private static Product publishedProduct(String name) {
        Product product = Product.openNew(UUID.randomUUID(), name, new BigDecimal("10.00"), Currency.ARS, 5);
        product.publish();
        return product;
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findPublished(eq(ProductStage.PUBLISHED), eq(0), captor.capture());
        return captor.getValue();
    }
}
