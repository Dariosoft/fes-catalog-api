package com.friendlyeshop.catalog.controller;

import com.friendlyeshop.catalog.model.dto.out.ProductResponse;
import com.friendlyeshop.catalog.model.dto.out.PublicationDetailResponse;
import com.friendlyeshop.catalog.model.dto.out.PublicationPageResponse;
import com.friendlyeshop.catalog.model.dto.request.PublishCatalogRequest;
import com.friendlyeshop.catalog.model.dto.out.PublishCatalogResponse;
import com.friendlyeshop.catalog.service.PublicationService;
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
@RequestMapping("/publication")
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping
    public PublicationPageResponse list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return publicationService.list(name, page, size);
    }

    @GetMapping("/{id}")
    public PublicationDetailResponse detail(@PathVariable UUID id) {
        return publicationService.detail(id);
    }

    @PostMapping(value = "/publish-catalog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PublishCatalogResponse publishCatalog(
            @RequestParam UUID ownerAccountId,
            @Valid @RequestBody(required = false) PublishCatalogRequest request) {
        return publicationService.publishCatalog(ownerAccountId, request);
    }

    @PostMapping("/publish/{id}")
    public ProductResponse publish(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        return publicationService.publish(ownerAccountId, id);
    }

    @PostMapping("/unpublish/{id}")
    public ProductResponse unpublish(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        return publicationService.unpublish(ownerAccountId, id);
    }
}
