package com.springmarket.identity.dto;

import java.util.Set;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        Set<String> roles) {}
