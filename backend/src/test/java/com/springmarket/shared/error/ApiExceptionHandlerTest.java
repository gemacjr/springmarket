package com.springmarket.shared.error;

import com.springmarket.inventory.InsufficientStockException;
import com.springmarket.order.InvalidOrderStateException;
import com.springmarket.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "app.jwt.secret=test-secret-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationErrors_returnProblemDetailsWithTypeAndDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void insufficientStock_mapsToConflictProblemDetails() {
        var problem = new ApiExceptionHandler()
                .handleDomainException(new InsufficientStockException("product-1"));

        assertEquals(409, problem.getStatus());
    }

    @Test
    void invalidOrderState_mapsToBadRequestProblemDetails() {
        var problem = new ApiExceptionHandler().handleDomainException(
                new InvalidOrderStateException(OrderStatus.DELIVERED, OrderStatus.PENDING));

        assertEquals(400, problem.getStatus());
    }
}
