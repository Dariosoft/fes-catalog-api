package com.friendlyeshop.catalog.model.dto.request;

import jakarta.validation.Valid;
import java.util.List;

public record PublishCatalogRequest(List<@Valid PublicationRequest> products) {
}
