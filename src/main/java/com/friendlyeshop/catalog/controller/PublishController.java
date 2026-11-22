package com.friendlyeshop.catalog.controller;

import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.PublishCatalogRequest;
import com.friendlyeshop.catalog.model.dto.PublishCatalogResponse;
import com.friendlyeshop.catalog.model.dto.PublishedProductPageResponse;
import com.friendlyeshop.catalog.service.ProductPublicationService;
import com.friendlyeshop.catalog.service.PublishedProductService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/publish")
public class PublishController {

    private final PublishedProductService publishedProductService;

    private final ProductPublicationService publicationService;

    public PublishController(PublishedProductService publishedProductService,
            ProductPublicationService publicationService) {
        this.publishedProductService = publishedProductService;
        this.publicationService = publicationService;
    }

    @GetMapping
    public PublishedProductPageResponse list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return publishedProductService.list(name, page, size);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PublishCatalogResponse publishCatalog(
            @RequestParam UUID ownerAccountId,
            @Valid @RequestBody(required = false) PublishCatalogRequest request) {
        return publicationService.publishCatalog(ownerAccountId, request);
    }

    @PostMapping("/products/{id}")
    public ProductResponse publish(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        return publicationService.publish(ownerAccountId, id);
    }

    @PostMapping("/products/{id}/unpublish")
    public ProductResponse unpublish(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        return publicationService.unpublish(ownerAccountId, id);
    }
}
