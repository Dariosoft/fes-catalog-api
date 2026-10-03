package com.friendlyeshop.catalog.service;

import com.friendlyeshop.catalog.client.storage.ImageContent;
import com.friendlyeshop.catalog.client.storage.ImageStorage;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductImageService {

    private final ProductImageRepository productImageRepository;

    private final ImageStorage imageStorage;

    public ProductImageService(ProductImageRepository productImageRepository, ImageStorage imageStorage) {
        this.productImageRepository = productImageRepository;
        this.imageStorage = imageStorage;
    }

    @Transactional(readOnly = true)
    public ImageContent read(UUID imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ProductNotFoundException("La imagen no existe"));
        return imageStorage.load(image.getObjectKey());
    }
}
