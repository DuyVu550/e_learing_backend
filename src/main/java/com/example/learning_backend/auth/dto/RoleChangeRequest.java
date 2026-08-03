package com.example.learning_backend.auth.dto;

import com.example.learning_backend.user.enums.AssignableRole;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(
    @NotNull AssignableRole role
) {
}
