package com.friendlyeshop.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class ProductRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void migrationCreatesSchemaWithoutTenantColumn() {
        @SuppressWarnings("null")
        List<String> columns = entityManager
                .createNativeQuery("select column_name from information_schema.columns where table_name = 'products'")
                .getResultList()
                .stream()
                .map(Object::toString)
                .toList();

        assertThat(columns).contains("owner_account_id", "stage", "currency", "deleted_at", "version");
        assertThat(columns).doesNotContain("tenant_id");
    }

    @Test
    void persistsProductWithNullableOwnerAndLowercaseStage() {
        Product product = Product.openNew(null, "Zapatillas", new BigDecimal("10.00"), Currency.ARS, null, null);
        productRepository.saveAndFlush(product);

        Object stage = entityManager.createNativeQuery("select stage from products where id = :id")
                .setParameter("id", product.getId())
                .getSingleResult();

        assertThat(stage).isEqualTo("draft");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getOwnerAccountId()).isNull();
    }

    @Test
    void persistsAndFindsImagesByProduct() {
        Product product = Product.openNew(UUID.randomUUID(), "Zapatillas", new BigDecimal("10.00"), Currency.USD, 2,
                null);
        productRepository.saveAndFlush(product);
        ProductImage image = ProductImage.of(UUID.randomUUID(), product.getId(), "products/x/y", "image/png");
        productImageRepository.saveAndFlush(image);

        List<ProductImage> images = productImageRepository.findByProductId(product.getId());

        assertThat(images).hasSize(1);
        assertThat(images.get(0).getObjectKey()).isEqualTo("products/x/y");
    }

    @SuppressWarnings("null")
    @Test
    void listExcludesDeletedAndOtherAccounts() {
        UUID owner = UUID.randomUUID();
        Product own = Product.openNew(owner, "Mio", new BigDecimal("1.00"), Currency.ARS, null, null);
        Product other = Product.openNew(UUID.randomUUID(), "Ajeno", new BigDecimal("2.00"), Currency.ARS, null, null);
        Product deleted = Product.openNew(owner, "Borrado", new BigDecimal("3.00"), Currency.ARS, null, null);
        deleted.deleteLogically(Instant.now());
        productRepository.saveAllAndFlush(List.of(own, other, deleted));

        List<Product> result = productRepository
                .findByOwnerAccountIdAndDeletedAtIsNull(owner, Pageable.unpaged())
                .getContent();

        assertThat(result).extracting(Product::getName).containsExactly("Mio");
    }

    @Test
    void findByIdIsScopedToOwner() {
        UUID owner = UUID.randomUUID();
        Product own = Product.openNew(owner, "Mio", new BigDecimal("1.00"), Currency.ARS, null, null);
        productRepository.saveAndFlush(own);

        assertThat(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(own.getId(), owner)).isPresent();
        assertThat(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(own.getId(), UUID.randomUUID()))
                .isEmpty();
    }

    @SuppressWarnings("null")
    @Test
    void findsAccountDraftsOnly() {
        UUID owner = UUID.randomUUID();
        Product draft = Product.openNew(owner, "Borrador", new BigDecimal("1.00"), Currency.ARS, null, null);
        Product published = Product.openNew(owner, "Publicado", new BigDecimal("2.00"), Currency.ARS, null, null);
        published.publish();
        productRepository.saveAllAndFlush(List.of(draft, published));

        List<Product> result = productRepository
                .findByOwnerAccountIdAndStageAndDeletedAtIsNull(owner, ProductStage.DRAFT);

        assertThat(result).extracting(Product::getName).containsExactly("Borrador");
    }

    @SuppressWarnings("null")
    @Test
    void findPublishedReturnsOnlyPubliclyVisibleProducts() {
        UUID owner = UUID.randomUUID();
        Product visible = publishedProduct(owner, "Visible", 3);
        Product otherVendor = publishedProduct(UUID.randomUUID(), "Otro vendedor", 8);
        Product draft = Product.openNew(owner, "Borrador", new BigDecimal("10.00"), Currency.ARS, 5, null);
        Product deleted = publishedProduct(owner, "Borrado", 5);
        deleted.deleteLogically(Instant.now());
        Product withoutStock = publishedProduct(owner, "Sin stock informado", null);
        Product zeroStock = publishedProduct(owner, "Sin stock cero", 0);
        Product withoutOwner = publishedProduct(owner, "Sin duenio", 4);
        productRepository.saveAllAndFlush(
                List.of(visible, otherVendor, draft, deleted, withoutStock, zeroStock, withoutOwner));
        entityManager.createNativeQuery("update products set owner_account_id = null where id = :id")
                .setParameter("id", withoutOwner.getId())
                .executeUpdate();
        entityManager.clear();

        List<Product> result = productRepository
                .findPublished(ProductStage.PUBLISHED, 0, Pageable.unpaged())
                .getContent();

        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("Visible", "Otro vendedor", "Sin stock informado");
    }

    @SuppressWarnings("null")
    @Test
    void findPublishedByNameIgnoresCaseButKeepsAccentsSignificant() {
        UUID owner = UUID.randomUUID();
        Product sneakers = publishedProduct(owner, "Zapatillas Running", 3);
        Product coffee = publishedProduct(owner, "Café molido", 2);
        productRepository.saveAllAndFlush(List.of(sneakers, coffee));

        List<Product> byCase = productRepository
                .findPublishedByName(ProductStage.PUBLISHED, 0, "zapatillas running", Pageable.unpaged())
                .getContent();
        List<Product> byAccent = productRepository
                .findPublishedByName(ProductStage.PUBLISHED, 0, "cafe molido", Pageable.unpaged())
                .getContent();

        assertThat(byCase).extracting(Product::getName).containsExactly("Zapatillas Running");
        assertThat(byAccent).isEmpty();
    }

    @Test
    void findsImagesForSeveralProductsInOneCall() {
        Product first = publishedProduct(UUID.randomUUID(), "Primero", 1);
        Product second = publishedProduct(UUID.randomUUID(), "Segundo", 2);
        productRepository.saveAllAndFlush(List.of(first, second));
        productImageRepository.saveAllAndFlush(List.of(
                ProductImage.of(UUID.randomUUID(), first.getId(), "products/p/1", "image/png"),
                ProductImage.of(UUID.randomUUID(), second.getId(), "products/p/2", "image/jpeg")));

        List<ProductImage> images = productImageRepository
                .findByProductIdIn(List.of(first.getId(), second.getId()));
        List<ProductImage> missing = productImageRepository.findByProductIdIn(List.of(UUID.randomUUID()));

        assertThat(images).extracting(ProductImage::getObjectKey)
                .containsExactlyInAnyOrder("products/p/1", "products/p/2");
        assertThat(missing).isEmpty();
    }

    @Test
    void findPublishedExcludesDraftAndUnpublishedProducts() {
        UUID owner = UUID.randomUUID();
        Product draft = Product.openNew(owner, "Borrador nuevo", new BigDecimal("10.00"), Currency.ARS, 3, null);
        Product unpublished = publishedProduct(owner, "Despublicado", 3);
        unpublished.unpublish();
        productRepository.saveAllAndFlush(List.of(draft, unpublished));

        List<Product> result = productRepository
                .findPublished(ProductStage.PUBLISHED, 0, Pageable.unpaged())
                .getContent();

        assertThat(result).isEmpty();
    }

    @Test
    void schemaRejectsNegativeStockSoItCanNeverReachThePublicReading() {
        Product negative = Product.openNew(UUID.randomUUID(), "Stock negativo", new BigDecimal("10.00"),
                Currency.ARS, -1, null);
        negative.publish();

        assertThatThrownBy(() -> productRepository.saveAndFlush(negative))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ordersByCreationDateDescendingWithStableIdTiebreakAcrossPages() {
        UUID owner = UUID.randomUUID();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        Product oldest = publishedProduct(owner, "Mas viejo", 1);
        Product tiedFirst = publishedProduct(owner, "Empate uno", 1);
        Product tiedSecond = publishedProduct(owner, "Empate dos", 1);
        Product newest = publishedProduct(owner, "Mas nuevo", 1);
        productRepository.saveAllAndFlush(List.of(oldest, tiedFirst, tiedSecond, newest));
        setCreatedAt(oldest, base);
        setCreatedAt(tiedFirst, base.plusSeconds(10));
        setCreatedAt(tiedSecond, base.plusSeconds(10));
        setCreatedAt(newest, base.plusSeconds(20));
        entityManager.clear();

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        List<Product> firstPage = productRepository
                .findPublished(ProductStage.PUBLISHED, 0, PageRequest.of(0, 2, sort))
                .getContent();
        List<Product> secondPage = productRepository
                .findPublished(ProductStage.PUBLISHED, 0, PageRequest.of(1, 2, sort))
                .getContent();

        List<Object> expectedOrder = entityManager.createNativeQuery("""
                select id from products
                where stage = 'published' and deleted_at is null and owner_account_id is not null
                  and (stock is null or stock > 0)
                order by created_at desc, id asc
                """).getResultList();
        List<String> pagedOrder = Stream.concat(firstPage.stream(), secondPage.stream())
                .map(product -> product.getId().toString())
                .toList();

        assertThat(pagedOrder).containsExactlyElementsOf(expectedOrder.stream().map(Object::toString).toList());
        assertThat(firstPage).extracting(Product::getName).first().isEqualTo("Mas nuevo");
        assertThat(secondPage).extracting(Product::getName).last().isEqualTo("Mas viejo");
    }

    @Test
    void migrationAddsNullableDescriptionColumn() {
        Object[] column = (Object[]) entityManager.createNativeQuery("""
                select data_type, character_maximum_length, is_nullable
                from information_schema.columns
                where table_name = 'products' and column_name = 'description'
                """).getSingleResult();

        assertThat(column[0]).isEqualTo("character varying");
        assertThat(((Number) column[1]).intValue()).isEqualTo(1000);
        assertThat(column[2]).isEqualTo("YES");
    }

    @Test
    void persistsAndRetrievesDescription() {
        Product product = Product.openNew(UUID.randomUUID(), "Café", new BigDecimal("10.00"), Currency.ARS, 3,
                "  Café de tueste medio  ");
        productRepository.saveAndFlush(product);
        entityManager.clear();

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();

        assertThat(reloaded.getDescription()).isEqualTo("Café de tueste medio");
    }

    @Test
    void findPublicByIdReturnsPublishedProductWithPositiveStock() {
        Product visible = publishedProductWithDescription(UUID.randomUUID(), "Visible", 3, "Descripción");
        productRepository.saveAndFlush(visible);
        entityManager.clear();

        assertThat(productRepository.findPublicById(visible.getId(), ProductStage.PUBLISHED, 0))
                .isPresent()
                .get()
                .extracting(Product::getDescription)
                .isEqualTo("Descripción");
    }

    @Test
    void findPublicByIdReturnsPublishedProductWithoutStockInformation() {
        Product withoutStock = publishedProduct(UUID.randomUUID(), "Sin stock informado", null);
        productRepository.saveAndFlush(withoutStock);
        entityManager.clear();

        assertThat(productRepository.findPublicById(withoutStock.getId(), ProductStage.PUBLISHED, 0)).isPresent();
    }

    @Test
    void findPublicByIdHidesDraftUnpublishedDeletedOwnerlessAndZeroStock() {
        UUID owner = UUID.randomUUID();
        Product draft = Product.openNew(owner, "Borrador", new BigDecimal("10.00"), Currency.ARS, 3, null);
        Product unpublished = publishedProduct(owner, "Despublicado", 3);
        unpublished.unpublish();
        Product deleted = publishedProduct(owner, "Borrado", 3);
        deleted.deleteLogically(Instant.now());
        Product ownerless = publishedProduct(owner, "Sin dueño", 3);
        Product zeroStock = publishedProduct(owner, "Sin stock cero", 0);
        productRepository.saveAllAndFlush(List.of(draft, unpublished, deleted, ownerless, zeroStock));
        entityManager.createNativeQuery("update products set owner_account_id = null where id = :id")
                .setParameter("id", ownerless.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(productRepository.findPublicById(draft.getId(), ProductStage.PUBLISHED, 0)).isEmpty();
        assertThat(productRepository.findPublicById(unpublished.getId(), ProductStage.PUBLISHED, 0)).isEmpty();
        assertThat(productRepository.findPublicById(deleted.getId(), ProductStage.PUBLISHED, 0)).isEmpty();
        assertThat(productRepository.findPublicById(ownerless.getId(), ProductStage.PUBLISHED, 0)).isEmpty();
        assertThat(productRepository.findPublicById(zeroStock.getId(), ProductStage.PUBLISHED, 0)).isEmpty();
        assertThat(productRepository.findPublicById(UUID.randomUUID(), ProductStage.PUBLISHED, 0)).isEmpty();
    }

    private static Product publishedProductWithDescription(UUID owner, String name, Integer stock,
            String description) {
        Product product = Product.openNew(owner, name, new BigDecimal("10.00"), Currency.ARS, stock, description);
        product.publish();
        return product;
    }

    private void setCreatedAt(Product product, Instant createdAt) {
        entityManager.createNativeQuery("update products set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", product.getId())
                .executeUpdate();
    }

    private static Product publishedProduct(UUID owner, String name, Integer stock) {
        Product product = Product.openNew(owner, name, new BigDecimal("10.00"), Currency.ARS, stock, null);
        product.publish();
        return product;
    }
}
