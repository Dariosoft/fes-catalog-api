package com.friendlyeshop.catalog.model.dto;

import java.util.List;

public record PublicationPageResponse(
        List<PublicationResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
