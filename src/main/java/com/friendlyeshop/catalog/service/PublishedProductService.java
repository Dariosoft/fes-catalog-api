package com.friendlyeshop.catalog.service;

import com.friendlyeshop.catalog.mapper.ProductResponseMapper;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.model.dto.PublishedProductPageResponse;
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
public class PublishedProductService {

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_SIZE = 24;

    private static final int MAX_SIZE = 100;

    private static final int MINIMUM_STOCK = 0;

    private static final Sort PAGE_SORT = Sort.by(Order.desc("createdAt"), Order.asc("id"));

    private final ProductRepository productRepository;

    private final ProductImageRepository productImageRepository;

    private final ProductResponseMapper responseMapper;

    public PublishedProductService(ProductRepository productRepository,
            ProductImageRepository productImageRepository, ProductResponseMapper responseMapper) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.responseMapper = responseMapper;
    }

    @Transactional(readOnly = true)
    public PublishedProductPageResponse list(String name, String rawPage, String rawSize) {
        int size = normalizeSize(rawSize);
        int page = normalizePage(rawPage);
        String filter = normalizeName(name);
        Page<Product> result = findPage(filter, page, size);
        if (result.getTotalElements() > 0 && page > result.getTotalPages()) {
            page = result.getTotalPages();
            result = findPage(filter, page, size);
        }
        if (result.isEmpty()) {
            return responseMapper.toPublishedPage(List.of(), Map.of(), page, size, 0, 0);
        }
        return responseMapper.toPublishedPage(result.getContent(), imagesByProductId(result.getContent()),
                page, size, result.getTotalElements(), result.getTotalPages());
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
