package com.example.learning_backend.auth.dto;

import java.util.Set;

public record UserResponse(
    Long id,
    String email,
    String fullName,
    String avatarUrl,
    String status,
    Set<String> roles
) {
}
