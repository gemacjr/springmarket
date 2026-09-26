package com.springmarket.payment;

import com.springmarket.shared.events.PaymentCompletedEvent;
import com.springmarket.shared.events.PaymentFailedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @Transactional
    public PaymentResult charge(
            UUID orderId, BigDecimal amount, String currency, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        PaymentResult result = paymentRepository
                .findByIdempotencyKey(idempotencyKey)
                .map(Payment::toResult)
                .orElseGet(() -> paymentRepository
                        .save(new Payment(
                                orderId,
                                idempotencyKey.endsWith("-FAIL")
                                        ? PaymentStatus.FAILURE
                                        : PaymentStatus.SUCCESS,
                                amount,
                                currency,
                                idempotencyKey,
                                Instant.now()))
                        .toResult());
        Instant occurredAt = Instant.now();
        if (result.status() == PaymentStatus.SUCCESS) {
            paymentEventPublisher.publish(new PaymentCompletedEvent(
                    UUID.randomUUID(), occurredAt, result.paymentId(), orderId));
        } else {
            paymentEventPublisher.publish(new PaymentFailedEvent(
                    UUID.randomUUID(), occurredAt, result.paymentId(), orderId));
        }
        return result;
    }
}
