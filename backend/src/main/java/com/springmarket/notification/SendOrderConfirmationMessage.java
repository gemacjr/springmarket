package com.springmarket.notification;

import java.math.BigDecimal;
import java.util.UUID;

public record SendOrderConfirmationMessage(
        String toEmail,
        UUID orderId,
        BigDecimal total) {}
