package com.example.learning_backend.forum.dto;

import jakarta.validation.constraints.NotBlank;

public record LessonCommentUpdateRequest(
    @NotBlank String content
) {
}
