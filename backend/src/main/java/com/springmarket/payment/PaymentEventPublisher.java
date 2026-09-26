package com.springmarket.payment;

import com.springmarket.shared.events.PaymentCompletedEvent;
import com.springmarket.shared.events.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PaymentCompletedEvent event) {
        publishAfterCommit(
                "payment.completed", event.orderId().toString(), event);
    }

    public void publish(PaymentFailedEvent event) {
        publishAfterCommit(
                "payment.failed", event.orderId().toString(), event);
    }

    private void publishAfterCommit(String topic, String key, Object event) {
        Runnable send = () -> {
            try {
                kafkaTemplate.send(topic, key, event).whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Could not publish payment event to {}", topic, exception);
                    }
                });
            } catch (RuntimeException exception) {
                log.error("Could not publish payment event to {}", topic, exception);
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
