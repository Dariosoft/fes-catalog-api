package com.friendlyeshop.catalog.model.dto;

import java.util.List;

public record PublishedProductPageResponse(
        List<PublishedProductResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
