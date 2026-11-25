package com.friendlyeshop.catalog.controller;

import com.friendlyeshop.catalog.model.dto.request.ProductRequest;
import com.friendlyeshop.catalog.model.dto.out.ProductResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam UUID ownerAccountId,
            @RequestParam(required = false) String name) {
        return productService.list(ownerAccountId, name);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @RequestParam(required = false) UUID ownerAccountId,
            @Valid @ModelAttribute ProductRequest request) {
        return productService.create(ownerAccountId, request);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse update(
            @PathVariable UUID id,
            @RequestParam UUID ownerAccountId,
            @Valid @ModelAttribute ProductRequest request) {
        return productService.update(ownerAccountId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam UUID ownerAccountId) {
        productService.deleteLogically(ownerAccountId, id);
    }
}
