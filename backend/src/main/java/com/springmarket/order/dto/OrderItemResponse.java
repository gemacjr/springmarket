package com.springmarket.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        int quantity,
        BigDecimal unitPrice,
        String currency) {}
