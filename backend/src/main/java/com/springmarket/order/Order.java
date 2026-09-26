package com.springmarket.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    private UUID id;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    Order(UUID buyerId, String currency, Instant now) {
        this.id = UUID.randomUUID();
        this.buyerId = buyerId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = BigDecimal.ZERO;
        this.currency = currency;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void addItem(String productId, int quantity, BigDecimal unitPrice, String currency) {
        items.add(new OrderItem(this, productId, quantity, unitPrice, currency));
        totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    void transitionTo(OrderStatus status, OrderStatusMachine statusMachine, Instant now) {
        statusMachine.assertCanTransition(this.status, status);
        this.status = status;
        this.updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getBuyerId() {
        return buyerId;
    }

    OrderStatus getStatus() {
        return status;
    }

    BigDecimal getTotalAmount() {
        return totalAmount;
    }

    String getCurrency() {
        return currency;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
