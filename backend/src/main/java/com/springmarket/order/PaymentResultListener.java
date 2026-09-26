package com.springmarket.order;

import com.springmarket.shared.events.PaymentCompletedEvent;
import com.springmarket.shared.events.PaymentFailedEvent;
import com.springmarket.shared.events.ProcessedEvent;
import com.springmarket.shared.events.ProcessedEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentResultListener {
    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;

    public PaymentResultListener(
            OrderService orderService,
            ProcessedEventRepository processedEventRepository) {
        this.orderService = orderService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "payment.completed", groupId = "springmarket-orders")
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        applyOnce(event.eventId(), event.orderId(), true);
    }

    @KafkaListener(topics = "payment.failed", groupId = "springmarket-orders")
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        applyOnce(event.eventId(), event.orderId(), false);
    }

    private void applyOnce(UUID eventId, UUID orderId, boolean paid) {
        if (processedEventRepository.existsByEventId(eventId)) {
            return;
        }
        orderService.applyPaymentResult(orderId, paid);
        processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
    }
}
