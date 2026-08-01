package com.example.learning_backend.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionTopicCreateRequest(
    @NotBlank @Size(max = 255) String name,
    String description
) {
}
