package com.springmarket.shared.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID paymentId,
        UUID orderId) {}
