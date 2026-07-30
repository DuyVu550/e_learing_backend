package com.example.learning_backend.assessment.dto;

import com.example.learning_backend.assessment.enums.AssessmentStatus;
import com.example.learning_backend.assessment.enums.AssessmentType;
import java.math.BigDecimal;

public record AssessmentResponse(
    Long id,
    Long courseId,
    Long lessonId,
    String title,
    String description,
    AssessmentType type,
    AssessmentStatus status,
    Integer timeLimitMinutes,
    Integer maxAttempts,
    BigDecimal passingScore
) {
}



