package com.friendlyeshop.catalog.service;

import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.mapper.ProductResponseMapper;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.PublishCatalogItem;
import com.friendlyeshop.catalog.model.dto.PublishCatalogRequest;
import com.friendlyeshop.catalog.model.dto.PublishCatalogResponse;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import com.friendlyeshop.catalog.repository.ProductRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductPublicationService {

    private final ProductRepository productRepository;

    private final ProductImageRepository productImageRepository;

    private final ProductResponseMapper responseMapper;

    public ProductPublicationService(ProductRepository productRepository,
            ProductImageRepository productImageRepository, ProductResponseMapper responseMapper) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.responseMapper = responseMapper;
    }

    @Transactional
    public ProductResponse publish(UUID ownerAccountId, UUID id) {
        Product product = findOwnedProduct(ownerAccountId, id);
        product.publish();
        return responseMapper.toResponse(product, productImageRepository.findByProductId(product.getId()));
    }

    @Transactional
    public ProductResponse unpublish(UUID ownerAccountId, UUID id) {
        Product product = findOwnedProduct(ownerAccountId, id);
        product.unpublish();
        return responseMapper.toResponse(product, productImageRepository.findByProductId(product.getId()));
    }

    @Transactional
    public PublishCatalogResponse publishCatalog(UUID ownerAccountId, PublishCatalogRequest request) {
        int published = publishOwnerlessProducts(ownerAccountId, request);
        published += publishAccountDrafts(ownerAccountId);
        return new PublishCatalogResponse(published);
    }

    private int publishOwnerlessProducts(UUID ownerAccountId, PublishCatalogRequest request) {
        if (request == null || request.products() == null || request.products().isEmpty()) {
            return 0;
        }
        for (PublishCatalogItem item : request.products()) {
            Product product = Product.openNew(null, item.name(), item.price(), item.currency(), item.stock());
            product.takeOwnership(ownerAccountId);
            product.publish();
            productRepository.save(product);
        }
        return request.products().size();
    }

    private int publishAccountDrafts(UUID ownerAccountId) {
        List<Product> drafts = productRepository
                .findByOwnerAccountIdAndStageAndDeletedAtIsNull(ownerAccountId, ProductStage.DRAFT);
        for (Product draft : drafts) {
            draft.publish();
        }
        return drafts.size();
    }

    private Product findOwnedProduct(UUID ownerAccountId, UUID id) {
        return productRepository.findByIdAndOwnerAccountIdAndDeletedAtIsNull(id, ownerAccountId)
                .orElseThrow(() -> new ProductNotFoundException("El producto no existe para la cuenta"));
    }
}
