package com.springmarket.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPlacedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID buyerId,
        BigDecimal totalAmount,
        String currency) {}
