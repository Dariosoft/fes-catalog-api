package com.friendlyeshop.catalog.client.storage;

import org.springframework.core.io.Resource;

public record ImageContent(Resource resource, String contentType) {
}
