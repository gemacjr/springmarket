package com.springmarket.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank String name,
        @NotBlank String description,
        @DecimalMin(value = "0.01") BigDecimal priceAmount,
        @NotBlank @Pattern(regexp = "USD") String currency,
        @NotBlank String category,
        boolean active) {}
