package com.springmarket.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOrderControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @Test
    void buyerCannotListAllOrders() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(user("buyer").roles("BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAllOrders() throws Exception {
        UUID buyerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(orderRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(new Order(
                        buyerId, "USD", Instant.parse("2026-09-26T12:00:00Z"))));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buyerId").value(buyerId.toString()));
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result ->
                        assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    void buyerCannotAccessOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .with(user("buyer").roles("BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
