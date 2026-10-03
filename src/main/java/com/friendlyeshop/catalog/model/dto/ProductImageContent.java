package com.friendlyeshop.catalog.model.dto;

import org.springframework.core.io.Resource;

public record ProductImageContent(Resource resource, String contentType) {
}
