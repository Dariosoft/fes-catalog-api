package com.friendlyeshop.catalog.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ProductStageConverter implements AttributeConverter<ProductStage, String> {
    @Override
    public String convertToDatabaseColumn(ProductStage stage) {
        return stage == null ? null : stage.value();
    }

    @Override
    public ProductStage convertToEntityAttribute(String value) {
        return value == null ? null : ProductStage.from(value);
    }
}
