package com.example.learning_backend.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionOptionRequest(
    @NotBlank @Size(max = 5000) String optionText,
    @NotNull Boolean correct,
    @NotNull Integer position
) {
}
