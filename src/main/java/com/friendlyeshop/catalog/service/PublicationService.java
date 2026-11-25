package com.friendlyeshop.catalog.service;

import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.mapper.ProductResponseMapper;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.dto.ProductDetails;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.Publication;
import com.friendlyeshop.catalog.model.dto.PublicationDetailResponse;
import com.friendlyeshop.catalog.model.dto.PublicationPageResponse;
import com.friendlyeshop.catalog.model.dto.PublishCatalogRequest;
import com.friendlyeshop.catalog.model.dto.PublishCatalogResponse;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import com.friendlyeshop.catalog.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationService {

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_SIZE = 24;

    private static final int MAX_SIZE = 100;

    private static final int MINIMUM_STOCK = 0;

    private static final Sort PAGE_SORT = Sort.by(Order.desc("createdAt"), Order.asc("id"));

    private final ProductRepository productRepository;

    private final ProductImageRepository productImageRepository;

    private final ProductResponseMapper responseMapper;

    private final ProductLookup productLookup;

    public PublicationService(ProductRepository productRepository,
            ProductImageRepository productImageRepository, ProductResponseMapper responseMapper,
            ProductLookup productLookup) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.responseMapper = responseMapper;
        this.productLookup = productLookup;
    }

    @Transactional(readOnly = true)
    public PublicationPageResponse list(String name, String rawPage, String rawSize) {
        int size = normalizeSize(rawSize);
        int page = normalizePage(rawPage);
        String filter = normalizeName(name);
        Page<Product> result = findPage(filter, page, size);
        if (result.getTotalElements() > 0 && page > result.getTotalPages()) {
            page = result.getTotalPages();
            result = findPage(filter, page, size);
        }
        if (result.isEmpty()) {
            return responseMapper.toPublicationPage(List.of(), Map.of(), page, size, 0, 0);
        }
        return responseMapper.toPublicationPage(result.getContent(), imagesByProductId(result.getContent()),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ProductResponse publish(UUID ownerAccountId, UUID id) {
        Product product = productLookup.findOwned(ownerAccountId, id);
        product.publish();
        return responseMapper.toResponse(product, productImageRepository.findByProductId(product.getId()));
    }

    @Transactional
    public ProductResponse unpublish(UUID ownerAccountId, UUID id) {
        Product product = productLookup.findOwned(ownerAccountId, id);
        product.unpublish();
        return responseMapper.toResponse(product, productImageRepository.findByProductId(product.getId()));
    }

    @Transactional(readOnly = true)
    public PublicationDetailResponse detail(UUID id) {
        Product product = productRepository.findPublicById(id, ProductStage.PUBLISHED, MINIMUM_STOCK)
                .orElseThrow(() -> new ProductNotFoundException("La publicación no existe"));
        List<ProductImage> images = productImageRepository.findByProductId(product.getId());
        return responseMapper.toPublicationDetail(product, images);
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
        for (Publication item : request.products()) {
            Product product = Product.openNew(null,
                    new ProductDetails(item.name(), null, item.price(), item.currency(), item.stock()));
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

    private Page<Product> findPage(String name, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, PAGE_SORT);
        return name == null
                ? productRepository.findPublished(ProductStage.PUBLISHED, MINIMUM_STOCK, pageable)
                : productRepository.findPublishedByName(ProductStage.PUBLISHED, MINIMUM_STOCK, name, pageable);
    }

    private Map<UUID, List<ProductImage>> imagesByProductId(List<Product> products) {
        List<UUID> productIds = products.stream()
                .map(Product::getId)
                .toList();
        return productImageRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));
    }

    private int normalizeSize(String rawSize) {
        return Math.min(parseOrDefault(rawSize, DEFAULT_SIZE), MAX_SIZE);
    }

    private int normalizePage(String rawPage) {
        return parseOrDefault(rawPage, DEFAULT_PAGE);
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int parseOrDefault(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            return parsed < 1 ? defaultValue : parsed;
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
