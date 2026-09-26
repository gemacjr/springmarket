package com.springmarket.payment;

import java.util.UUID;

public record PaymentResult(UUID paymentId, PaymentStatus status, String idempotencyKey) {}
