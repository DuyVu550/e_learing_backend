package com.example.learning_backend.auth.dto;

public record AuthResponse(
    UserResponse user,
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInSeconds
) {
}
