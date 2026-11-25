package com.friendlyeshop.catalog.model.dto.in;

import java.io.InputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

public record ProductImageContentDetails(Resource resource, String contentType) {
    public static ProductImageContentDetails from(InputStream stream, String contentType) {
        return new ProductImageContentDetails(new InputStreamResource(stream), contentType);
    }
}
