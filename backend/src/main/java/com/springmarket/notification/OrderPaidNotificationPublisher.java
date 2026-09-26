package com.springmarket.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class OrderPaidNotificationPublisher {
    private static final Logger log =
            LoggerFactory.getLogger(OrderPaidNotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderPaidNotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(SendOrderConfirmationMessage message) {
        Runnable send = () -> {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitConfig.NOTIFICATIONS_EXCHANGE,
                        RabbitConfig.ORDER_CONFIRMATION_ROUTING_KEY,
                        message);
            } catch (RuntimeException exception) {
                log.error(
                        "Could not publish order confirmation for {}",
                        message.orderId(),
                        exception);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        send.run();
                    }
                });
    }
}
