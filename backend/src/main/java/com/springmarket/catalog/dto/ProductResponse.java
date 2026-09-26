package com.springmarket.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        String id,
        UUID sellerId,
        String name,
        String description,
        BigDecimal priceAmount,
        String currency,
        String category,
        boolean active,
        Instant createdAt) {}
