package com.friendlyeshop.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.friendlyeshop.catalog.exception.InvalidProductException;
import com.friendlyeshop.catalog.model.dto.ProductImageResponse;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.PublishCatalogResponse;
import com.friendlyeshop.catalog.model.dto.PublicationPageResponse;
import com.friendlyeshop.catalog.model.dto.PublicationResponse;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import com.friendlyeshop.catalog.service.PublicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicationController.class)
class PublicationControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @Test
    void listsPublicationsWithoutCredentials() throws Exception {
        UUID imageId = UUID.randomUUID();
        PublicationResponse item = new PublicationResponse(UUID.randomUUID(), "Café molido",
                new BigDecimal("1234.50"), Currency.ARS, 7, List.of(new ProductImageResponse(imageId,
                        "https://api.fes.test/catalog/images/" + imageId)));
        when(publicationService.list(null, null, null))
                .thenReturn(new PublicationPageResponse(List.of(item), 1, 24, 57, 3));

        mockMvc.perform(get("/catalog/publication"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(24))
                .andExpect(jsonPath("$.totalItems").value(57))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(item.id().toString()))
                .andExpect(jsonPath("$.items[0].name").value("Café molido"))
                .andExpect(jsonPath("$.items[0].price").value(1234.50))
                .andExpect(jsonPath("$.items[0].currency").value("ARS"))
                .andExpect(jsonPath("$.items[0].stock").value(7))
                .andExpect(jsonPath("$.items[0].images[0].id").value(imageId.toString()))
                .andExpect(jsonPath("$.items[0].images[0].url")
                        .value("https://api.fes.test/catalog/images/" + imageId))
                .andExpect(jsonPath("$.items[0].ownerAccountId").doesNotExist())
                .andExpect(jsonPath("$.items[0].stage").doesNotExist());
    }

    @Test
    void forwardsNameAndPaginationParameters() throws Exception {
        when(publicationService.list("cafe", "2", "12")).thenReturn(page());

        mockMvc.perform(get("/catalog/publication")
                        .param("name", "cafe")
                        .param("page", "2")
                        .param("size", "12"))
                .andExpect(status().isOk());

        verify(publicationService).list("cafe", "2", "12");
    }

    @Test
    void keepsNonNumericParametersOutOfTheBadRequestPath() throws Exception {
        when(publicationService.list(null, "abc", "0")).thenReturn(page());

        mockMvc.perform(get("/catalog/publication")
                        .param("page", "abc")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(24));
    }

    @Test
    void returnsEmptyItemsArrayWhenThereAreNoProducts() throws Exception {
        when(publicationService.list(null, null, null))
                .thenReturn(new PublicationPageResponse(List.of(), 1, 24, 0, 0));

        mockMvc.perform(get("/catalog/publication"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void returnsEmptyImagesArrayForProductWithoutImages() throws Exception {
        when(publicationService.list(null, null, null)).thenReturn(new PublicationPageResponse(
                List.of(new PublicationResponse(UUID.randomUUID(), "Zapatillas", new BigDecimal("10.00"),
                        Currency.USD, 2, List.of())),
                1, 24, 1, 1));

        mockMvc.perform(get("/catalog/publication"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].images").isEmpty());
    }

    @Test
    void returnsTheClampedPageReportedByTheService() throws Exception {
        when(publicationService.list(null, "5", null))
                .thenReturn(new PublicationPageResponse(List.of(), 2, 24, 30, 2));

        mockMvc.perform(get("/catalog/publication").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(24))
                .andExpect(jsonPath("$.totalItems").value(30))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void publishesProduct() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(publicationService.publish(owner, id)).thenReturn(productResponse());

        mockMvc.perform(post("/catalog/publication/publish/{id}", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("draft"));
    }

    @Test
    void unpublishesProduct() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(publicationService.unpublish(owner, id)).thenReturn(productResponse());

        mockMvc.perform(
                        post("/catalog/publication/unpublish/{id}", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsPublicationConflict() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(publicationService.publish(owner, id)).thenThrow(new InvalidProductException("sin dueño"));

        mockMvc.perform(post("/catalog/publication/publish/{id}", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflicto_de_publicacion"));
    }

    @Test
    void publishesCatalog() throws Exception {
        UUID owner = UUID.randomUUID();
        when(publicationService.publishCatalog(eq(owner), any())).thenReturn(new PublishCatalogResponse(2));

        mockMvc.perform(post("/catalog/publication/publish-catalog")
                        .param("ownerAccountId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"products\":[{\"name\":\"Nuevo\",\"price\":8.00,\"currency\":\"USD\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(2));
    }

    @Test
    void publishesCatalogWithNoProducts() throws Exception {
        UUID owner = UUID.randomUUID();
        when(publicationService.publishCatalog(eq(owner), any())).thenReturn(new PublishCatalogResponse(0));

        mockMvc.perform(post("/catalog/publication/publish-catalog")
                        .param("ownerAccountId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(0));
    }

    @Test
    void noLongerServesThePreviousPublicationPaths() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/catalog/products/published"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/catalog/publish"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/catalog/publish").param("ownerAccountId", owner.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/catalog/publish/products/{id}", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isNotFound());
    }

    private ProductResponse productResponse() {
        return new ProductResponse(UUID.randomUUID(), UUID.randomUUID(), "Zapatillas", new BigDecimal("10.00"),
                Currency.ARS, 3, ProductStage.DRAFT, List.of(), Instant.now(), Instant.now());
    }

    private PublicationPageResponse page() {
        return new PublicationPageResponse(
                List.of(new PublicationResponse(UUID.randomUUID(), "Zapatillas", new BigDecimal("10.00"),
                        Currency.ARS, 2, List.of())),
                1, 24, 1, 1);
    }
}
