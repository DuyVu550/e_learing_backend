package com.example.learning_backend.enrollment.dto;

import java.math.BigDecimal;

public record CourseProgressResponse(
    Long courseId,
    long totalLessons,
    long completedLessons,
    BigDecimal progressPercentage,
    boolean completed
) {
}
