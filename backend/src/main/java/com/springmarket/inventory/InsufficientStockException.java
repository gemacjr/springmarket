package com.springmarket.inventory;

import com.springmarket.shared.error.DomainException;
import org.springframework.http.HttpStatus;

public class InsufficientStockException extends DomainException {
    public InsufficientStockException(String productId) {
        super(HttpStatus.CONFLICT, "Insufficient stock for product " + productId);
    }
}
