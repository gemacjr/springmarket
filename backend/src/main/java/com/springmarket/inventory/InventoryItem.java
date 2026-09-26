package com.springmarket.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    @Column(name = "product_id", length = 36)
    private String productId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Version
    @Column(nullable = false)
    private long version;

    protected InventoryItem() {}

    InventoryItem(String productId, int availableQty) {
        this.productId = productId;
        this.availableQty = availableQty;
    }

    void reserve(int quantity) {
        if (quantity <= 0 || availableQty < quantity) {
            throw new InsufficientStockException(productId);
        }
        availableQty -= quantity;
        reservedQty += quantity;
    }

    void release(int quantity) {
        if (quantity <= 0 || reservedQty < quantity) {
            throw new IllegalArgumentException("Cannot release unreserved inventory");
        }
        reservedQty -= quantity;
        availableQty += quantity;
    }
}
