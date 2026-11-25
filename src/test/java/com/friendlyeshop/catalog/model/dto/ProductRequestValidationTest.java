package com.friendlyeshop.catalog.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.model.dto.request.ProductRequest;
import com.friendlyeshop.catalog.model.enums.Currency;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankNameNullPriceAndNullCurrency() {
        ProductRequest form = new ProductRequest("  ", null, null, null, null, List.of());

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(form);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "price", "currency");
    }

    @Test
    void acceptsValidForm() {
        ProductRequest form = new ProductRequest("Zapatillas", null, new BigDecimal("10.00"), Currency.USD, 3,
                List.of());

        assertThat(validator.validate(form)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void acceptsAbsentOrBlankDescription(String description) {
        ProductRequest form = formWithDescription(description);

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void acceptsDescriptionWithExactlyOneThousandCharacters() {
        ProductRequest form = formWithDescription("a".repeat(1000));

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void rejectsDescriptionWithMoreThanTheMaximum() {
        ProductRequest form = formWithDescription("a".repeat(1001));

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(form);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("description");
    }

    private ProductRequest formWithDescription(String description) {
        return new ProductRequest("Zapatillas", description, new BigDecimal("10.00"), Currency.ARS, 1, List.of());
    }
}
