package com.springmarket.order;

import com.springmarket.order.dto.OrderResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({
    SecurityConfig.class,
    JwtAuthFilter.class,
    ProblemDetailAuthenticationEntryPoint.class,
    ProblemDetailAccessDeniedHandler.class,
    ApiExceptionHandler.class
})
class OrderControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private JwtService jwtService;

    @Test
    void authenticatedBuyerCanPlaceOrder() throws Exception {
        UserPrincipal buyer = buyer();
        when(orderService.placeOrder(buyer.userId())).thenReturn(order(buyer.userId()));

        mockMvc.perform(post("/api/v1/orders").with(user(buyer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void authenticatedBuyerCanListOwnOrders() throws Exception {
        UserPrincipal buyer = buyer();
        when(orderService.mine(buyer.userId())).thenReturn(List.of(order(buyer.userId())));

        mockMvc.perform(get("/api/v1/orders/mine").with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buyerId").value(buyer.userId().toString()));
    }

    @Test
    void authenticatedBuyerCanGetOwnOrder() throws Exception {
        UserPrincipal buyer = buyer();
        OrderResponse response = order(buyer.userId());
        when(orderService.getOwn(response.id(), buyer.userId())).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/{id}", response.id()).with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()));
    }

    @Test
    void anonymousCannotPlaceOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    private UserPrincipal buyer() {
        return new UserPrincipal(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "buyer@example.com",
                Set.of("BUYER"),
                true);
    }

    private OrderResponse order(UUID buyerId) {
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        return new OrderResponse(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                buyerId,
                OrderStatus.PENDING,
                new BigDecimal("19.99"),
                "USD",
                List.of(),
                now,
                now);
    }
}
