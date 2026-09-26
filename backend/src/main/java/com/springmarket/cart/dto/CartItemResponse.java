package com.springmarket.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        String productId,
        int quantity,
        BigDecimal unitPrice,
        String currency) {}
