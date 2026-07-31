package com.example.learning_backend.enrollment.dto;

import com.example.learning_backend.enrollment.enums.LessonProgressStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LessonProgressRequest(
    @NotNull LessonProgressStatus status,
    @Min(0) Integer lastPositionSeconds
) {
}
