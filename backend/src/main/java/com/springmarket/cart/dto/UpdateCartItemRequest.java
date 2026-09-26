package com.springmarket.cart.dto;

import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(@Positive int quantity) {}
