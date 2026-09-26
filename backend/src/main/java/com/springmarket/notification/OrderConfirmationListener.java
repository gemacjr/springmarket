package com.springmarket.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderConfirmationListener {
    private final MailNotificationService notificationService;

    public OrderConfirmationListener(MailNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_CONFIRMATION_QUEUE)
    public void onOrderPaid(SendOrderConfirmationMessage message) {
        notificationService.sendOrderConfirmation(
                message.toEmail(), message.orderId(), message.total());
    }
}
