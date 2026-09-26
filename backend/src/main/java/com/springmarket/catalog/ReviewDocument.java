package com.springmarket.catalog;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("reviews")
public record ReviewDocument(
        @Id String id,
        String productId,
        UUID buyerId,
        int rating,
        String comment,
        Instant createdAt) {}
