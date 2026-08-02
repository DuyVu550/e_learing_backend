package com.example.learning_backend.forum.dto;

import jakarta.validation.constraints.NotBlank;

public record LessonCommentRequest(
    @NotBlank String content,
    Long parentId
) {
}
