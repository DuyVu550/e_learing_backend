package com.example.learning_backend.assessment.dto;

import com.example.learning_backend.assessment.enums.AssessmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AssessmentCreateRequest(
    @NotBlank @Size(max = 255) String title,
    String description,
    @NotNull AssessmentType type,
    Long lessonId,
    Integer timeLimitMinutes,
    Integer maxAttempts,
    BigDecimal passingScore
) {
}



