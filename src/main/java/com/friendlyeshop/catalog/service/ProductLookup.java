package com.friendlyeshop.catalog.service;

import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.repository.ProductRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProductLookup {

    private final ProductRepository productRepository;

    public ProductLookup(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product findOwned(UUID ownerAccountId, UUID id) {
        return productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, ownerAccountId)
                .orElseThrow(() -> new ProductNotFoundException("El producto no existe para la cuenta"));
    }
}
