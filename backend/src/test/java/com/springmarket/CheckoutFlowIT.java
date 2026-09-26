package com.springmarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmarket.notification.OrderConfirmationListener;
import com.springmarket.notification.RabbitConfig;
import com.springmarket.notification.SendOrderConfirmationMessage;
import com.springmarket.payment.PaymentRepository;
import com.springmarket.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CheckoutFlowIT extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderConfirmationListener orderConfirmationListener;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private JavaMailSenderImpl mailSender;

    @Test
    void registeredBuyerChecksOutProductAndTriggersPaymentEvents() throws Exception {
        String sellerToken = register("seller@example.com", "SELLER");
        String buyerToken = register("buyer@example.com", "BUYER");

        JsonNode product = json(mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Testcontainers Coffee",
                                  "description": "A checkout-flow product",
                                  "priceAmount": 18.50,
                                  "currency": "USD",
                                  "category": "GROCERY",
                                  "initialStock": 5
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Testcontainers Coffee"))
                .andReturn()
                .getResponse()
                .getContentAsString());

        String productId = product.get("id").asText();
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":2}
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        JsonNode order = json(mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(37.00))
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(order.get("id").asText()).isNotBlank();
        assertThat(paymentRepository.count()).isEqualTo(1);
        verify(kafkaTemplate, atLeastOnce()).send(eq("order.placed"), any(), any());
        verify(kafkaTemplate, atLeastOnce()).send(eq("payment.completed"), any(), any());

        ArgumentCaptor<SendOrderConfirmationMessage> notification =
                ArgumentCaptor.forClass(SendOrderConfirmationMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.NOTIFICATIONS_EXCHANGE),
                eq(RabbitConfig.ORDER_CONFIRMATION_ROUTING_KEY),
                notification.capture());

        orderConfirmationListener.onOrderPaid(notification.getValue());

        ArgumentCaptor<SimpleMailMessage> mail =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("buyer@example.com");
        assertThat(mail.getValue().getSubject())
                .isEqualTo("Order confirmation " + order.get("id").asText());
        assertThat(mail.getValue().getText())
                .isEqualTo("Your order %s has been paid. Total: 37.00"
                        .formatted(order.get("id").asText()));
    }

    private String register(String email, String role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"Password1!",
                                  "roles":["%s"]
                                }
                                """.formatted(email, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value(role))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(response).get("accessToken").asText();
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
