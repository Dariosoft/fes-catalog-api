package com.friendlyeshop.catalog.model.dto.request;

import com.friendlyeshop.catalog.model.enums.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record ProductRequest(
        @NotBlank String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0") BigDecimal price,
        @NotNull Currency currency,
        @Min(0) Integer stock,
        List<MultipartFile> images) {
}
