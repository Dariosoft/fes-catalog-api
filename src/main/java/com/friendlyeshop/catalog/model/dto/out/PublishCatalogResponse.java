package com.friendlyeshop.catalog.model.dto.out;

public record PublishCatalogResponse(int published) {
    public static PublishCatalogResponse from(int published) {
        return new PublishCatalogResponse(published);
    }
}
