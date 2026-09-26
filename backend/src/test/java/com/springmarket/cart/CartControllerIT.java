package com.springmarket.cart;

import com.springmarket.cart.dto.CartItemResponse;
import com.springmarket.cart.dto.CartResponse;
import com.springmarket.shared.error.ApiExceptionHandler;
import com.springmarket.shared.security.JwtAuthFilter;
import com.springmarket.shared.security.JwtService;
import com.springmarket.shared.security.ProblemDetailAccessDeniedHandler;
import com.springmarket.shared.security.ProblemDetailAuthenticationEntryPoint;
import com.springmarket.shared.security.SecurityConfig;
import com.springmarket.shared.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import({
    SecurityConfig.class,
    JwtAuthFilter.class,
    ProblemDetailAuthenticationEntryPoint.class,
    ProblemDetailAccessDeniedHandler.class,
    ApiExceptionHandler.class
})
class CartControllerIT {
    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void anonymousCannotReadCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void authenticatedUserAddsItemToOwnCart() throws Exception {
        when(cartService.addItem(eq(USER_ID), eq("product-1"), eq(2))).thenReturn(cart());

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "product-1", "quantity": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(19.99))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    private UserPrincipal principal() {
        return new UserPrincipal(USER_ID, "buyer@example.com", Set.of("BUYER"), true);
    }

    private CartResponse cart() {
        return new CartResponse(
                UUID.randomUUID(),
                USER_ID,
                List.of(new CartItemResponse(
                        "product-1", 2, new BigDecimal("19.99"), "USD")),
                Instant.parse("2026-09-26T12:00:00Z"));
    }
}
