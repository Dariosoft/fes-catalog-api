package com.friendlyeshop.catalog.controller;

import com.friendlyeshop.catalog.client.storage.ImageContent;
import com.friendlyeshop.catalog.client.storage.ImageStorage;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog/images")
public class ProductImageController {

    private final ProductImageRepository productImageRepository;

    private final ImageStorage imageStorage;

    public ProductImageController(ProductImageRepository productImageRepository, ImageStorage imageStorage) {
        this.productImageRepository = productImageRepository;
        this.imageStorage = imageStorage;
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<Resource> read(@PathVariable UUID imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ProductNotFoundException("La imagen no existe"));
        ImageContent content = imageStorage.load(image.getObjectKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(content.resource());
    }
}
