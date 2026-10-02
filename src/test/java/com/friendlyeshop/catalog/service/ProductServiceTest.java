package com.friendlyeshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.friendlyeshop.catalog.client.storage.ImageContent;
import com.friendlyeshop.catalog.client.storage.ImageStorage;
import com.friendlyeshop.catalog.client.storage.StoredImage;
import com.friendlyeshop.catalog.config.CatalogProperties;
import com.friendlyeshop.catalog.exception.InvalidProductException;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.mapper.ProductResponseMapper;
import com.friendlyeshop.catalog.model.Currency;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductStage;
import com.friendlyeshop.catalog.model.dto.ProductForm;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private final FakeImageStorage imageStorage = new FakeImageStorage();

    private ProductService service;

    @BeforeEach
    void setUp() {
        ProductResponseMapper mapper = new ProductResponseMapper(new CatalogProperties("https://api.fes.test"));
        service = new ProductService(productRepository, productImageRepository, imageStorage, mapper);
    }

    @Test
    void createsDraftWithImages() {
        UUID owner = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("images", "photo.png", "image/png", "data".getBytes());
        stubSaves();

        ProductResponse response = service.create(owner, form(List.of(file)));

        assertThat(response.ownerAccountId()).isEqualTo(owner);
        assertThat(response.stage()).isEqualTo(ProductStage.DRAFT);
        assertThat(response.images()).hasSize(1);
        assertThat(imageStorage.storedCount).isEqualTo(1);
    }

    @Test
    void createsDraftWithoutImages() {
        stubSaves();

        ProductResponse response = service.create(UUID.randomUUID(), form(List.of()));

        assertThat(response.images()).isEmpty();
        assertThat(imageStorage.storedCount).isZero();
    }

    @Test
    void createsDraftWithoutOwner() {
        stubSaves();

        ProductResponse response = service.create(null, form(List.of()));

        assertThat(response.ownerAccountId()).isNull();
    }

    @Test
    void rejectsMissingCurrency() {
        ProductForm form = new ProductForm("Zapatillas", new BigDecimal("10.00"), null, null, List.of());

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), form))
                .isInstanceOf(InvalidProductException.class);
    }

    @Test
    void listsOwnedProducts() {
        UUID owner = UUID.randomUUID();
        Product product = Product.openNew(owner, "Zapatillas", new BigDecimal("5.00"), Currency.ARS, null);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByOwnerAccountIdAndDeletedAtIsNull(eq(owner), any(Pageable.class)))
                .thenReturn(page);
        lenient().when(productImageRepository.findByProductId(product.getId())).thenReturn(List.of());

        List<ProductResponse> responses = service.list(owner, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).ownerAccountId()).isEqualTo(owner);
    }

    @Test
    void listsEmptyWhenAccountHasNoProducts() {
        UUID owner = UUID.randomUUID();
        when(productRepository.findByOwnerAccountIdAndDeletedAtIsNull(eq(owner), any(Pageable.class)))
                .thenReturn(Page.empty());

        List<ProductResponse> responses = service.list(owner, null);

        assertThat(responses).isEmpty();
    }

    @Test
    void filtersByNameWhenProvided() {
        UUID owner = UUID.randomUUID();
        when(productRepository.findByOwnerAccountIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                        eq(owner), eq("mat"), any(Pageable.class)))
                .thenReturn(Page.empty());

        List<ProductResponse> responses = service.list(owner, "mat");

        assertThat(responses).isEmpty();
        verify(productRepository)
                .findByOwnerAccountIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                        eq(owner), eq("mat"), any(Pageable.class));
    }

    @Test
    void updatesOwnedProduct() {
        UUID owner = UUID.randomUUID();
        Product product = Product.openNew(owner, "Viejo", new BigDecimal("5.00"), Currency.ARS, 1);
        when(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(product.getId(), owner))
                .thenReturn(Optional.of(product));
        lenient().when(productImageRepository.findByProductId(product.getId())).thenReturn(List.of());
        stubSaves();

        ProductResponse response = service.update(owner, product.getId(),
                new ProductForm("Nuevo", new BigDecimal("9.00"), Currency.USD, 2, List.of()));

        assertThat(response.name()).isEqualTo("Nuevo");
        assertThat(response.currency()).isEqualTo(Currency.USD);
        assertThat(response.stock()).isEqualTo(2);
    }

    @Test
    void deletesLogicallyOwnedProduct() {
        UUID owner = UUID.randomUUID();
        Product product = Product.openNew(owner, "Zapatillas", new BigDecimal("5.00"), Currency.ARS, null);
        when(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(product.getId(), owner))
                .thenReturn(Optional.of(product));

        service.deleteLogically(owner, product.getId());

        assertThat(product.isDeleted()).isTrue();
    }

    @Test
    void rejectsDeletingForeignProduct() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteLogically(owner, id))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void rejectsUpdateForForeignAccount() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(owner, id, form(List.of())))
                .isInstanceOf(ProductNotFoundException.class);
    }

    private void stubSaves() {
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productImageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProductForm form(List<MultipartFile> images) {
        return new ProductForm("Zapatillas", new BigDecimal("10.00"), Currency.ARS, 3, images);
    }

    private static final class FakeImageStorage implements ImageStorage {

        private int storedCount;

        @Override
        public StoredImage store(UUID productId, UUID imageId, MultipartFile file) {
            storedCount++;
            return new StoredImage("products/%s/%s".formatted(productId, imageId), file.getContentType());
        }

        @Override
        public ImageContent load(String objectKey) {
            throw new UnsupportedOperationException();
        }
    }
}
