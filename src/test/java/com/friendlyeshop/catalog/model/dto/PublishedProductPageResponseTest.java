package com.friendlyeshop.catalog.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.model.enums.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.json.JsonMapper;

class PublishedProductPageResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void serializesOnlyThePublicPageFields() throws JSONException {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        PublishedProductPageResponse response = new PublishedProductPageResponse(
                List.of(new PublishedProductResponse(productId, "Café molido 500 g", new BigDecimal("1234.50"),
                        Currency.ARS, 7, List.of(new ProductImageResponse(imageId,
                                "https://api.fes.test/catalog/images/" + imageId)))),
                1, 24, 57, 3);

        String json = jsonMapper.writeValueAsString(response);

        JSONAssert.assertEquals("""
                {
                  "items": [{
                    "id": "%s",
                    "name": "Café molido 500 g",
                    "price": 1234.50,
                    "currency": "ARS",
                    "stock": 7,
                    "images": [{"id": "%s", "url": "https://api.fes.test/catalog/images/%s"}]
                  }],
                  "page": 1,
                  "size": 24,
                  "totalItems": 57,
                  "totalPages": 3
                }
                """.formatted(productId, imageId, imageId), json, true);
        assertThat(json).doesNotContain("ownerAccountId", "stage", "createdAt", "updatedAt");
    }

    @Test
    void serializesEmptyPageWithZeroTotals() throws JSONException {
        PublishedProductPageResponse response = new PublishedProductPageResponse(List.of(), 1, 24, 0, 0);

        String json = jsonMapper.writeValueAsString(response);

        JSONAssert.assertEquals("""
                {"items": [], "page": 1, "size": 24, "totalItems": 0, "totalPages": 0}
                """, json, true);
    }
}
