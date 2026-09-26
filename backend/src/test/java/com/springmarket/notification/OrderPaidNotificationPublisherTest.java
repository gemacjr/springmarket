package com.springmarket.notification;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPaidNotificationPublisherTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publish_routesConfirmationToNotificationsExchange() {
        var publisher = new OrderPaidNotificationPublisher(rabbitTemplate);
        var message = new SendOrderConfirmationMessage(
                "buyer@example.com",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("39.98"));

        publisher.publish(message);

        verify(rabbitTemplate).convertAndSend(
                RabbitConfig.NOTIFICATIONS_EXCHANGE,
                RabbitConfig.ORDER_CONFIRMATION_ROUTING_KEY,
                message);
    }
}
