package com.springmarket.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    protected CartItem() {}

    CartItem(Cart cart, String productId, int quantity, BigDecimal unitPrice, String currency) {
        this.id = UUID.randomUUID();
        this.cart = cart;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.currency = currency;
    }

    String getProductId() {
        return productId;
    }

    int getQuantity() {
        return quantity;
    }

    BigDecimal getUnitPrice() {
        return unitPrice;
    }

    String getCurrency() {
        return currency;
    }

    void addQuantity(int quantity, BigDecimal unitPrice, String currency) {
        this.quantity += quantity;
        this.unitPrice = unitPrice;
        this.currency = currency;
    }

    void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
