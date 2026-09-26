package com.springmarket.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        Set<String> roles) {
    public RegisterRequest {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
