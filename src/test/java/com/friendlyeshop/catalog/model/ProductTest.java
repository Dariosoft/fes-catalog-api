package com.friendlyeshop.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.friendlyeshop.catalog.exception.InvalidProductException;
import com.friendlyeshop.catalog.model.dto.in.ProductDetails;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final UUID OWNER = UUID.randomUUID();

    private static final BigDecimal PRICE = new BigDecimal("1200.00");

    @Test
    void isBornAsDraft() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, 10));

        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
        assertThat(product.isDeleted()).isFalse();
    }

    @Test
    void publishingRequiresOwner() {
        Product product = Product.openNew(null, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, null));

        assertThatThrownBy(product::publish).isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void publishingRequiresName() {
        Product product = Product.openNew(OWNER, new ProductDetails("  ", null, PRICE, Currency.ARS, null));

        assertThatThrownBy(product::publish).isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void publishingRequiresPrice() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, null, Currency.ARS, null));

        assertThatThrownBy(product::publish).isInstanceOf(InvalidProductException.class);
        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
    }

    @Test
    void publishesWithOwnerNameAndPrice() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, null));

        product.publish();

        assertThat(product.getStage()).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void unpublishingKeepsOwner() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, null));
        product.publish();

        product.unpublish();

        assertThat(product.getStage()).isEqualTo(ProductStage.DRAFT);
        assertThat(product.getOwnerAccountId()).isEqualTo(OWNER);
    }

    @Test
    void logicalDeletionMarksDeletedAt() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, null));
        Instant now = Instant.parse("2026-10-01T12:00:00Z");

        product.deleteLogically(now);

        assertThat(product.isDeleted()).isTrue();
        assertThat(product.getDeletedAt()).isEqualTo(now);
    }

    @Test
    void belongsToMatchesOwner() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, null));

        assertThat(product.belongsTo(OWNER)).isTrue();
        assertThat(product.belongsTo(UUID.randomUUID())).isFalse();
    }

    @Test
    void normalizesAbsentDescriptionToNull() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, null));

        assertThat(product.getDescription()).isNull();
    }

    @Test
    void normalizesBlankDescriptionToNull() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", "   ", PRICE, Currency.ARS, null));

        assertThat(product.getDescription()).isNull();
    }

    @Test
    void normalizesEmptyDescriptionToNull() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", "", PRICE, Currency.ARS, null));

        assertThat(product.getDescription()).isNull();
    }

    @Test
    void keepsDescriptionContentAndAccents() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", "Café de tueste medio", PRICE, Currency.ARS, null));

        assertThat(product.getDescription()).isEqualTo("Café de tueste medio");
    }

    @Test
    void stripsSurroundingWhitespaceFromDescription() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", "  Café  ", PRICE, Currency.ARS, null));

        assertThat(product.getDescription()).isEqualTo("Café");
    }

    @Test
    void updateNormalizesAndPreservesDescription() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", "Vieja", PRICE, Currency.ARS, null));

        product.update(
                new ProductDetails("Nuevas", "  Nueva descripción con acentos: ñandú  ", PRICE, Currency.USD, 2));

        assertThat(product.getDescription()).isEqualTo("Nueva descripción con acentos: ñandú");
    }

    @Test
    void updateBlanksDescriptionToNull() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", "Algo", PRICE, Currency.ARS, null));

        product.update(new ProductDetails("Nuevas", "   ", PRICE, Currency.USD, 2));

        assertThat(product.getDescription()).isNull();
    }

    @Test
    void publishingAndUnpublishingKeepDescription() {
        Product product = Product.openNew(OWNER,
                new ProductDetails("Zapatillas", "Descripción estable", PRICE, Currency.ARS, null));

        product.publish();
        assertThat(product.getDescription()).isEqualTo("Descripción estable");

        product.unpublish();
        assertThat(product.getDescription()).isEqualTo("Descripción estable");
    }

    @Test
    void publishedProductIsReservable() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, 3));
        product.publish();

        assertThat(product.isReservable()).isTrue();
    }

    @Test
    void draftProductIsNotReservable() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, 3));

        assertThat(product.isReservable()).isFalse();
    }

    @Test
    void deletedProductIsNotReservable() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, 3));
        product.publish();
        product.deleteLogically(Instant.now());

        assertThat(product.isReservable()).isFalse();
    }

    @Test
    void unknownStockCoversAnyQuantity() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, null));

        assertThat(product.canCover(1)).isTrue();
        assertThat(product.canCover(Integer.MAX_VALUE)).isTrue();
    }

    @Test
    void knownStockCoversOnlyUpToAvailableUnits() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, 5));

        assertThat(product.canCover(4)).isTrue();
        assertThat(product.canCover(5)).isTrue();
        assertThat(product.canCover(6)).isFalse();
    }

    @Test
    void reservingReducesStock() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, 5));

        product.reserve(3);

        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void reservingUnknownStockKeepsItUnlimited() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, null));

        product.reserve(3);

        assertThat(product.getStock()).isNull();
    }

    @Test
    void reservingMoreThanAvailableFailsWithoutChangingStock() {
        Product product = Product.openNew(OWNER, new ProductDetails("Zapatillas", null, PRICE, Currency.ARS, 2));

        assertThatThrownBy(() -> product.reserve(3)).isInstanceOf(IllegalStateException.class);
        assertThat(product.getStock()).isEqualTo(2);
    }
}
