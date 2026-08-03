package com.example.learning_backend.auth.dto;

import com.example.learning_backend.user.enums.AssignableRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(max = 255) String fullName,
    /** Optional; omitted means STUDENT, so clients written before this field keep working. */
    AssignableRole role
) {
}
