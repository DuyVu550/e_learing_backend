package com.example.learning_backend.assessment.dto;

import com.example.learning_backend.assessment.enums.AssessmentCompositionMode;
import com.example.learning_backend.assessment.enums.AssessmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssessmentCreateRequest(
    @NotBlank @Size(max = 255) String title,
    String description,
    @NotNull AssessmentType type,
    Long lessonId,
    AssessmentCompositionMode compositionMode,
    LocalDateTime availableFrom,
    LocalDateTime availableUntil,
    @Min(1) Integer timeLimitMinutes,
    @Min(1) Integer maxAttempts,
    @DecimalMin(value = "0.0") @DecimalMax(value = "100.0") BigDecimal passingScore,
    Boolean shuffleQuestions,
    Boolean shuffleOptions
) {
}



