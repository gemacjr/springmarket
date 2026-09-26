package com.springmarket.cart;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {}

    Cart(UUID userId, Instant updatedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.updatedAt = updatedAt;
    }

    void addItem(
            String productId,
            int quantity,
            BigDecimal unitPrice,
            String currency,
            Instant updatedAt) {
        items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.addQuantity(quantity, unitPrice, currency),
                        () -> items.add(new CartItem(this, productId, quantity, unitPrice, currency)));
        this.updatedAt = updatedAt;
    }

    boolean updateItem(String productId, int quantity, Instant updatedAt) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .map(item -> {
                    item.setQuantity(quantity);
                    this.updatedAt = updatedAt;
                    return true;
                })
                .orElse(false);
    }

    boolean removeItem(String productId, Instant updatedAt) {
        boolean removed = items.removeIf(item -> item.getProductId().equals(productId));
        if (removed) {
            this.updatedAt = updatedAt;
        }
        return removed;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    List<CartItem> getItems() {
        return List.copyOf(items);
    }
}
