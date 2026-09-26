package com.springmarket.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotBlank String productId,
        @Positive int quantity) {}
