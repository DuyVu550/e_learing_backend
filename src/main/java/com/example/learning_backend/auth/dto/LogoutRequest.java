package com.example.learning_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank String refreshToken
) {
}
