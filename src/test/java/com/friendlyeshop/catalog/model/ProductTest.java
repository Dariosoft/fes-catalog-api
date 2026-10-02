package com.friendlyeshop.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.friendlyeshop.catalog.exception.InvalidProductException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final UUID OWNER = UUID.randomUUID();

    private static final BigDecimal PRICE = new BigDecimal("1200.00");

    @Test
    void isBornAsDraft() {
        Product product = Product.openNew(OWNER, "Zapatillas", PRICE, Currency.ARS, 10);

        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
        assertThat(product.isDeleted()).isFalse();
    }

    @Test
    void publishingRequiresOwner() {
        Product product = Product.openNew(null, "Zapatillas", PRICE, Currency.ARS, null);

        assertThatThrownBy(product::publish).isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void publishingRequiresName() {
        Product product = Product.openNew(OWNER, "  ", PRICE, Currency.ARS, null);

        assertThatThrownBy(product::publish).isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void publishingRequiresPrice() {
        Product product = Product.openNew(OWNER, "Zapatillas", null, Currency.ARS, null);

        assertThatThrownBy(product::publish).isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void publishesWithOwnerNameAndPrice() {
        Product product = Product.openNew(OWNER, "Zapatillas", PRICE, Currency.ARS, null);

        product.publish();

        assertThat(product.getStage()).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void unpublishingKeepsOwner() {
        Product product = Product.openNew(OWNER, "Zapatillas", PRICE, Currency.ARS, null);
        product.publish();

        product.unpublish();

        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
        assertThat(product.getOwnerAccountId()).isEqualTo(OWNER);
    }

    @Test
    void logicalDeletionMarksDeletedAt() {
        Product product = Product.openNew(OWNER, "Zapatillas", PRICE, Currency.ARS, null);
        Instant now = Instant.parse("2026-10-01T12:00:00Z");

        product.deleteLogically(now);

        assertThat(product.isDeleted()).isTrue();
        assertThat(product.getDeletedAt()).isEqualTo(now);
    }

    @Test
    void belongsToMatchesOwner() {
        Product product = Product.openNew(OWNER, "Zapatillas", PRICE, Currency.ARS, null);

        assertThat(product.belongsTo(OWNER)).isTrue();
        assertThat(product.belongsTo(UUID.randomUUID())).isFalse();
    }
}
