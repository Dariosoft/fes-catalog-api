package com.friendlyeshop.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.model.Currency;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.ProductStage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
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
        Product product = Product.openNew(null, "Zapatillas", new BigDecimal("10.00"), Currency.ARS, null);
        productRepository.saveAndFlush(product);

        Object stage = entityManager.createNativeQuery("select stage from products where id = :id")
                .setParameter("id", product.getId())
                .getSingleResult();

        assertThat(stage).isEqualTo("draft");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getOwnerAccountId()).isNull();
    }

    @Test
    void persistsAndFindsImagesByProduct() {
        Product product = Product.openNew(UUID.randomUUID(), "Zapatillas", new BigDecimal("10.00"), Currency.USD, 2);
        productRepository.saveAndFlush(product);
        ProductImage image = ProductImage.of(UUID.randomUUID(), product.getId(), "products/x/y", "image/png");
        productImageRepository.saveAndFlush(image);

        List<ProductImage> images = productImageRepository.findByProductId(product.getId());

        assertThat(images).hasSize(1);
        assertThat(images.get(0).getObjectKey()).isEqualTo("products/x/y");
    }

    @Test
    void listExcludesDeletedAndOtherAccounts() {
        UUID owner = UUID.randomUUID();
        Product own = Product.openNew(owner, "Mio", new BigDecimal("1.00"), Currency.ARS, null);
        Product other = Product.openNew(UUID.randomUUID(), "Ajeno", new BigDecimal("2.00"), Currency.ARS, null);
        Product deleted = Product.openNew(owner, "Borrado", new BigDecimal("3.00"), Currency.ARS, null);
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
        Product own = Product.openNew(owner, "Mio", new BigDecimal("1.00"), Currency.ARS, null);
        productRepository.saveAndFlush(own);

        assertThat(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(own.getId(), owner)).isPresent();
        assertThat(productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(own.getId(), UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void findsAccountDraftsOnly() {
        UUID owner = UUID.randomUUID();
        Product draft = Product.openNew(owner, "Borrador", new BigDecimal("1.00"), Currency.ARS, null);
        Product published = Product.openNew(owner, "Publicado", new BigDecimal("2.00"), Currency.ARS, null);
        published.publish();
        productRepository.saveAllAndFlush(List.of(draft, published));

        List<Product> result = productRepository
                .findByOwnerAccountIdAndStageAndDeletedAtIsNull(owner, ProductStage.DRAFT);

        assertThat(result).extracting(Product::getName).containsExactly("Borrador");
    }
}
