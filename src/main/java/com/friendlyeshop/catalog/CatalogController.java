package com.friendlyeshop.catalog;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog")
public class CatalogController {
    @GetMapping
    public Map<String, Object> catalog() {
        return Map.of("service", "catalog-api", "status", "ready", "products", List.of());
    }
}
