package com.springmarket.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResult charge(
            UUID orderId, BigDecimal amount, String currency, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        return paymentRepository
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
    }
}
