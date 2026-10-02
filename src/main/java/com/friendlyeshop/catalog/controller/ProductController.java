package com.friendlyeshop.catalog.controller;

import com.friendlyeshop.catalog.model.dto.ProductForm;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.PublishCatalogRequest;
import com.friendlyeshop.catalog.model.dto.PublishCatalogResponse;
import com.friendlyeshop.catalog.service.ProductPublicationService;
import com.friendlyeshop.catalog.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog")
public class ProductController {

    private final ProductService productService;

    private final ProductPublicationService publicationService;

    public ProductController(ProductService productService, ProductPublicationService publicationService) {
        this.productService = productService;
        this.publicationService = publicationService;
    }

    @GetMapping("/products")
    public List<ProductResponse> list(@RequestParam UUID ownerAccountId) {
        return productService.list(ownerAccountId);
    }

    @PostMapping(path = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @RequestParam(required = false) UUID ownerAccountId,
            @Valid @ModelAttribute ProductForm form) {
        return productService.create(ownerAccountId, form);
    }

    @PutMapping(path = "/products/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse update(
            @PathVariable UUID id,
            @RequestParam UUID ownerAccountId,
            @Valid @ModelAttribute ProductForm form) {
        return productService.update(ownerAccountId, id, form);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        productService.deleteLogically(ownerAccountId, id);
    }

    @PostMapping("/products/{id}/publish")
    public ProductResponse publish(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        return publicationService.publish(ownerAccountId, id);
    }

    @PostMapping("/products/{id}/unpublish")
    public ProductResponse unpublish(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        return publicationService.unpublish(ownerAccountId, id);
    }

    @PostMapping(path = "/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PublishCatalogResponse publishCatalog(
            @RequestParam UUID ownerAccountId,
            @Valid @RequestBody(required = false) PublishCatalogRequest request) {
        return publicationService.publishCatalog(ownerAccountId, request);
    }
}
