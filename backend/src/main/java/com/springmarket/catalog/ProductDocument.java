package com.springmarket.catalog;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("products")
public record ProductDocument(
        @Id String id,
        UUID sellerId,
        String name,
        String description,
        BigDecimal priceAmount,
        String currency,
        String category,
        boolean active,
        Instant createdAt) implements Serializable {}
