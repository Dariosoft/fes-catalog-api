package com.friendlyeshop.catalog.model.dto;

import com.friendlyeshop.catalog.model.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record ProductForm(
        @NotBlank String name,
        @NotNull @DecimalMin("0") BigDecimal price,
        @NotNull Currency currency,
        @Min(0) Integer stock,
        List<MultipartFile> images) {
}
