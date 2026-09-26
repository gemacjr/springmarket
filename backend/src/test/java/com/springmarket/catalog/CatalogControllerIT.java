package com.springmarket.catalog;

import com.springmarket.catalog.dto.ProductResponse;
import com.springmarket.shared.error.ApiExceptionHandler;
import com.springmarket.shared.security.JwtAuthFilter;
import com.springmarket.shared.security.JwtService;
import com.springmarket.shared.security.ProblemDetailAccessDeniedHandler;
import com.springmarket.shared.security.ProblemDetailAuthenticationEntryPoint;
import com.springmarket.shared.security.SecurityConfig;
import com.springmarket.shared.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
@Import({
    SecurityConfig.class,
    JwtAuthFilter.class,
    ProblemDetailAuthenticationEntryPoint.class,
    ProblemDetailAccessDeniedHandler.class,
    ApiExceptionHandler.class
})
class CatalogControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;
    @MockitoBean
    private JwtService jwtService;

    @Test
    void productReadsArePublic() throws Exception {
        when(catalogService.list(0, 20)).thenReturn(new PageImpl<>(java.util.List.of(product())));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Coffee"));
    }

    @Test
    void sellerCanCreateProduct() throws Exception {
        UserPrincipal seller = principal("SELLER");
        when(catalogService.create(eq(seller.userId()), any())).thenReturn(product());

        mockMvc.perform(post("/api/v1/products")
                        .with(user(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Coffee",
                                  "description": "Single-origin beans",
                                  "priceAmount": 18.50,
                                  "currency": "USD",
                                  "category": "GROCERY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(seller.userId().toString()));
    }

    @Test
    void buyerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(user(principal("BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Coffee",
                                  "description": "Single-origin beans",
                                  "priceAmount": 18.50,
                                  "currency": "USD",
                                  "category": "GROCERY"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    private UserPrincipal principal(String role) {
        return new UserPrincipal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                role.toLowerCase() + "@example.com",
                Set.of(role),
                true);
    }

    private ProductResponse product() {
        return new ProductResponse(
                "product-1",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Coffee",
                "Single-origin beans",
                new BigDecimal("18.50"),
                "USD",
                "GROCERY",
                true,
                Instant.parse("2026-09-26T12:00:00Z"));
    }
}
