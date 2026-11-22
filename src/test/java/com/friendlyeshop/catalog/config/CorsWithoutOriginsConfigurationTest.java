package com.friendlyeshop.catalog.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.friendlyeshop.catalog.controller.PublishController;
import com.friendlyeshop.catalog.service.ProductPublicationService;
import com.friendlyeshop.catalog.service.PublishedProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublishController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = "fes.cors.browser-origins=")
class CorsWithoutOriginsConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublishedProductService publishedProductService;

    @MockitoBean
    private ProductPublicationService publicationService;

    @Test
    void authorizesNoBrowserOriginWhenTheListIsEmpty() throws Exception {
        mockMvc.perform(get("/catalog/publish").header(HttpHeaders.ORIGIN, "https://shop.fes.test"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
