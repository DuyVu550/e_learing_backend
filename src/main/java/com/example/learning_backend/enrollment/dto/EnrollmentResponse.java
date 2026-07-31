package com.example.learning_backend.enrollment.dto;

import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import java.time.LocalDateTime;

public record EnrollmentResponse(
    Long id,
    Long courseId,
    EnrollmentStatus status,
    LocalDateTime enrolledAt,
    LocalDateTime completedAt
) {
}
