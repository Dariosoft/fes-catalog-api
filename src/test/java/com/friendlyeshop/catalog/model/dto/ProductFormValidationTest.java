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

class ProductFormValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankNameNullPriceAndNullCurrency() {
        ProductForm form = new ProductForm("  ", null, null, null, List.of());

        Set<ConstraintViolation<ProductForm>> violations = validator.validate(form);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "price", "currency");
    }

    @Test
    void acceptsValidForm() {
        ProductForm form = new ProductForm("Zapatillas", new BigDecimal("10.00"), Currency.USD, 3, List.of());

        assertThat(validator.validate(form)).isEmpty();
    }
}
