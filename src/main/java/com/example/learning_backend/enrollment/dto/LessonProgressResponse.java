package com.example.learning_backend.enrollment.dto;

import com.example.learning_backend.enrollment.enums.LessonProgressStatus;
import java.time.LocalDateTime;

public record LessonProgressResponse(
    Long id,
    Long lessonId,
    LessonProgressStatus status,
    Integer lastPositionSeconds,
    LocalDateTime completedAt
) {
}
