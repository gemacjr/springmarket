package com.springmarket.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        String id,
        String productId,
        UUID buyerId,
        int rating,
        String comment,
        Instant createdAt) {}
