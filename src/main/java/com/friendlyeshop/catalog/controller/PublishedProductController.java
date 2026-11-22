package com.friendlyeshop.catalog.controller;

import com.friendlyeshop.catalog.model.dto.PublishedProductPageResponse;
import com.friendlyeshop.catalog.service.PublishedProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog/products")
public class PublishedProductController {

    private final PublishedProductService publishedProductService;

    public PublishedProductController(PublishedProductService publishedProductService) {
        this.publishedProductService = publishedProductService;
    }

    @GetMapping("/published")
    public PublishedProductPageResponse list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return publishedProductService.list(name, page, size);
    }
}
