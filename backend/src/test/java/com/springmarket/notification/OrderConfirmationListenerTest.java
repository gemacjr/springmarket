package com.springmarket.notification;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationListenerTest {
    @Mock
    private JavaMailSender mailSender;

    @Test
    void onOrderPaid_sendsEmail() {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MailNotificationService notificationService = new MailNotificationService(mailSender);
        OrderConfirmationListener listener = new OrderConfirmationListener(notificationService);

        listener.onOrderPaid(new SendOrderConfirmationMessage(
                "buyer@example.com", orderId, new BigDecimal("39.98")));

        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertEquals("buyer@example.com", message.getValue().getTo()[0]);
        assertEquals("Order confirmation " + orderId, message.getValue().getSubject());
        assertEquals(
                "Your order " + orderId + " has been paid. Total: 39.98",
                message.getValue().getText());
    }
}
