package com.friendlyeshop.catalog.model.dto.out;

import java.util.List;

public record PublicationPageResponse(
        List<PublicationResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
    public static PublicationPageResponse from(List<PublicationResponse> items, int page, int size,
            long totalItems, int totalPages) {
        return new PublicationPageResponse(items, page, size, totalItems, totalPages);
    }
}
