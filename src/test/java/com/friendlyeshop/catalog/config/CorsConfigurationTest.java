package com.friendlyeshop.catalog.config;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.friendlyeshop.catalog.controller.PublicationController;
import com.friendlyeshop.catalog.model.dto.PublicationPageResponse;
import com.friendlyeshop.catalog.service.PublicationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicationController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = "fes.cors.browser-origins=https://shop.fes.test,https://admin.fes.test")
class CorsConfigurationTest {

    private static final String STORE_ORIGIN = "https://shop.fes.test";

    private static final String FOREIGN_ORIGIN = "https://not-the-store.test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @Test
    void allowsStorefrontOriginOnGet() throws Exception {
        when(publicationService.list(isNull(), isNull(), isNull()))
                .thenReturn(new PublicationPageResponse(List.of(), 1, 24, 0, 0));

        mockMvc.perform(get("/catalog/publication").header(HttpHeaders.ORIGIN, STORE_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, STORE_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void answersPreflightWithGetMethod() throws Exception {
        mockMvc.perform(options("/catalog/publication")
                        .header(HttpHeaders.ORIGIN, STORE_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, STORE_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void doesNotAuthorizeForeignOrigins() throws Exception {
        mockMvc.perform(get("/catalog/publication").header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void doesNotAuthorizeActuatorRoutes() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, STORE_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
