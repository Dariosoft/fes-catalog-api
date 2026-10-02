package com.friendlyeshop.catalog.model.dto;

import jakarta.validation.Valid;
import java.util.List;

public record PublishCatalogRequest(@Valid List<PublishCatalogItem> products) {
}
