package com.friendlyeshop.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.friendlyeshop.catalog.exception.InvalidProductException;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.exception.StorageUnavailableException;
import com.friendlyeshop.catalog.model.Currency;
import com.friendlyeshop.catalog.model.ProductStage;
import com.friendlyeshop.catalog.model.dto.ProductForm;
import com.friendlyeshop.catalog.model.dto.ProductResponse;
import com.friendlyeshop.catalog.model.dto.PublishCatalogResponse;
import com.friendlyeshop.catalog.service.ProductPublicationService;
import com.friendlyeshop.catalog.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductPublicationService publicationService;

    @Test
    void listsProductsForAccount() throws Exception {
        UUID owner = UUID.randomUUID();
        when(productService.list(owner, null)).thenReturn(List.of(response()));

        mockMvc.perform(get("/catalog/products").param("ownerAccountId", owner.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Zapatillas"));
    }

    @Test
    void rejectsListingWithoutOwner() throws Exception {
        mockMvc.perform(get("/catalog/products"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("datos_invalidos"));
    }

    @Test
    void createsProductWithImages() throws Exception {
        UUID owner = UUID.randomUUID();
        when(productService.create(any(), any())).thenReturn(response());

        mockMvc.perform(multipart("/catalog/products")
                        .file(new MockMultipartFile("images", "photo.png", "image/png", "data".getBytes()))
                        .param("name", "Zapatillas")
                        .param("price", "10.00")
                        .param("currency", "ARS")
                        .param("stock", "3")
                        .param("ownerAccountId", owner.toString()))
                .andExpect(status().isCreated());

        ArgumentCaptor<ProductForm> captor = ArgumentCaptor.forClass(ProductForm.class);
        verify(productService).create(eq(owner), captor.capture());
        assertThat(captor.getValue().images()).hasSize(1);
    }

    @Test
    void createsProductWithoutOwner() throws Exception {
        when(productService.create(isNull(), any())).thenReturn(response());

        mockMvc.perform(multipart("/catalog/products")
                        .param("name", "Zapatillas")
                        .param("price", "10.00")
                        .param("currency", "ARS"))
                .andExpect(status().isCreated());

        verify(productService).create(isNull(), any(ProductForm.class));
    }

    @Test
    void rejectsInvalidCurrency() throws Exception {
        mockMvc.perform(multipart("/catalog/products")
                        .param("name", "Zapatillas")
                        .param("price", "10.00")
                        .param("currency", "EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("datos_invalidos"));
    }

    @Test
    void updatesProduct() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(productService.update(any(), any(), any())).thenReturn(response());

        mockMvc.perform(multipart(HttpMethod.PUT, "/catalog/products/{id}", id)
                        .param("name", "Nuevas")
                        .param("price", "12.00")
                        .param("currency", "USD")
                        .param("ownerAccountId", owner.toString()))
                .andExpect(status().isOk());

        verify(productService).update(eq(owner), eq(id), any(ProductForm.class));
    }

    @Test
    void deletesProductLogically() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/catalog/products/{id}", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isNoContent());

        verify(productService).deleteLogically(owner, id);
    }

    @Test
    void publishesProduct() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(publicationService.publish(owner, id)).thenReturn(response());

        mockMvc.perform(post("/catalog/products/{id}/publish", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("draft"));
    }

    @Test
    void unpublishesProduct() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(publicationService.unpublish(owner, id)).thenReturn(response());

        mockMvc.perform(post("/catalog/products/{id}/unpublish", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsPublicationConflict() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(publicationService.publish(owner, id)).thenThrow(new InvalidProductException("sin dueño"));

        mockMvc.perform(post("/catalog/products/{id}/publish", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflicto_de_publicacion"));
    }

    @Test
    void publishesCatalog() throws Exception {
        UUID owner = UUID.randomUUID();
        when(publicationService.publishCatalog(eq(owner), any())).thenReturn(new PublishCatalogResponse(2));

        mockMvc.perform(post("/catalog/publish")
                        .param("ownerAccountId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"products\":[{\"name\":\"Nuevo\",\"price\":8.00,\"currency\":\"USD\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(2));
    }

    @Test
    void returnsNotFoundForUnknownProduct() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        doThrow(new ProductNotFoundException("no existe")).when(productService).deleteLogically(owner, id);

        mockMvc.perform(delete("/catalog/products/{id}", id).param("ownerAccountId", owner.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("producto_no_encontrado"));
    }

    @Test
    void reportsStorageUnavailable() throws Exception {
        when(productService.create(any(), any()))
                .thenThrow(new StorageUnavailableException("minio down", new RuntimeException()));

        mockMvc.perform(multipart("/catalog/products")
                        .param("name", "Zapatillas")
                        .param("price", "10.00")
                        .param("currency", "ARS"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("almacenamiento_no_disponible"));
    }

    @Test
    void publishesCatalogWithNoProducts() throws Exception {
        UUID owner = UUID.randomUUID();
        when(publicationService.publishCatalog(eq(owner), any())).thenReturn(new PublishCatalogResponse(0));

        mockMvc.perform(post("/catalog/publish")
                        .param("ownerAccountId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(0));
    }

    private ProductResponse response() {
        return new ProductResponse(UUID.randomUUID(), UUID.randomUUID(), "Zapatillas", new BigDecimal("10.00"),
                Currency.ARS, 3, ProductStage.DRAFT, List.of(), Instant.now(), Instant.now());
    }
}
