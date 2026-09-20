package com.friendlyeshop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CatalogControllerTest {
    @Test
    void identifiesService() {
        assertThat(new CatalogController().catalog()).containsEntry("service", "catalog-api");
    }
}
