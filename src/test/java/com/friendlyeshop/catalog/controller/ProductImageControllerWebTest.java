package com.friendlyeshop.catalog.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.friendlyeshop.catalog.client.storage.ImageContent;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.service.ProductImageService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductImageController.class)
class ProductImageControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductImageService productImageService;

    @Test
    void streamsImageWithContentType() throws Exception {
        UUID imageId = UUID.randomUUID();
        when(productImageService.read(imageId))
                .thenReturn(new ImageContent(new ByteArrayResource("bytes".getBytes()), "image/png"));

        mockMvc.perform(get("/catalog/images/{imageId}", imageId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(content().bytes("bytes".getBytes()));
    }

    @Test
    void returnsNotFoundWhenImageIsMissing() throws Exception {
        UUID imageId = UUID.randomUUID();
        when(productImageService.read(imageId))
                .thenThrow(new ProductNotFoundException("La imagen no existe"));

        mockMvc.perform(get("/catalog/images/{imageId}", imageId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("producto_no_encontrado"));
    }
}
