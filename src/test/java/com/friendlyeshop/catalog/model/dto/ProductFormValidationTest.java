package com.friendlyeshop.catalog.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

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

class ProductFormValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankNameNullPriceAndNullCurrency() {
        ProductForm form = new ProductForm("  ", null, null, null, null, List.of());

        Set<ConstraintViolation<ProductForm>> violations = validator.validate(form);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "price", "currency");
    }

    @Test
    void acceptsValidForm() {
        ProductForm form = new ProductForm("Zapatillas", null, new BigDecimal("10.00"), Currency.USD, 3, List.of());

        assertThat(validator.validate(form)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void acceptsAbsentOrBlankDescription(String description) {
        ProductForm form = formWithDescription(description);

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void acceptsDescriptionWithExactlyOneThousandCharacters() {
        ProductForm form = formWithDescription("a".repeat(1000));

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void rejectsDescriptionWithMoreThanTheMaximum() {
        ProductForm form = formWithDescription("a".repeat(1001));

        Set<ConstraintViolation<ProductForm>> violations = validator.validate(form);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("description");
    }

    private ProductForm formWithDescription(String description) {
        return new ProductForm("Zapatillas", description, new BigDecimal("10.00"), Currency.ARS, 1, List.of());
    }
}
