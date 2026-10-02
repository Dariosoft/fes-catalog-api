package com.friendlyeshop.catalog.service;

import com.friendlyeshop.catalog.client.storage.ImageStorage;
import com.friendlyeshop.catalog.client.storage.StoredImage;
import com.friendlyeshop.catalog.exception.InvalidProductException;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.mapper.ProductResponseMapper;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.dto.ProductForm;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import com.friendlyeshop.catalog.repository.ProductRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    private final ProductImageRepository productImageRepository;

    private final ImageStorage imageStorage;

    private final ProductResponseMapper responseMapper;

    public ProductService(ProductRepository productRepository, ProductImageRepository productImageRepository,
            ImageStorage imageStorage, ProductResponseMapper responseMapper) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.imageStorage = imageStorage;
        this.responseMapper = responseMapper;
    }

    @Transactional
    public ProductResponse create(UUID ownerAccountId, ProductForm form) {
        validateForm(form);
        Product product = Product.openNew(ownerAccountId, form.name(), form.price(), form.currency(), form.stock());
        productRepository.save(product);
        List<ProductImage> images = storeImages(product.getId(), form.images());
        return responseMapper.toResponse(product, images);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(UUID ownerAccountId) {
        List<Product> products = productRepository
                .findByOwnerAccountIdAndDeletedAtIsNull(ownerAccountId, Pageable.unpaged())
                .getContent();
        return products.stream()
                .map(product -> responseMapper.toResponse(
                        product, productImageRepository.findByProductId(product.getId())))
                .toList();
    }

    @Transactional
    public ProductResponse update(UUID ownerAccountId, UUID id, ProductForm form) {
        validateForm(form);
        Product product = findOwnedProduct(ownerAccountId, id);
        product.update(form.name(), form.price(), form.currency(), form.stock());
        List<ProductImage> images = new ArrayList<>(productImageRepository.findByProductId(product.getId()));
        images.addAll(storeImages(product.getId(), form.images()));
        return responseMapper.toResponse(product, images);
    }

    @Transactional
    public void deleteLogically(UUID ownerAccountId, UUID id) {
        Product product = findOwnedProduct(ownerAccountId, id);
        product.deleteLogically(Instant.now());
    }

    private Product findOwnedProduct(UUID ownerAccountId, UUID id) {
        return productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, ownerAccountId)
                .orElseThrow(() -> new ProductNotFoundException("El producto no existe para la cuenta"));
    }

    private void validateForm(ProductForm form) {
        if (form.name() == null || form.name().isBlank()) {
            throw new InvalidProductException("El nombre del producto es obligatorio");
        }
        if (form.price() == null || form.price().signum() < 0) {
            throw new InvalidProductException("El precio del producto debe ser mayor o igual a cero");
        }
        if (form.currency() == null) {
            throw new InvalidProductException("La moneda del producto es obligatoria");
        }
        if (form.stock() != null && form.stock() < 0) {
            throw new InvalidProductException("El stock del producto no puede ser negativo");
        }
    }

    private List<ProductImage> storeImages(UUID productId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<ProductImage> images = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            UUID imageId = UUID.randomUUID();
            StoredImage stored = imageStorage.store(productId, imageId, file);
            images.add(ProductImage.of(imageId, productId, stored.objectKey(), stored.contentType()));
        }
        if (images.isEmpty()) {
            return List.of();
        }
        return productImageRepository.saveAll(images);
    }
}
