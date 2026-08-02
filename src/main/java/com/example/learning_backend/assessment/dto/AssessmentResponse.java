package com.example.learning_backend.assessment.dto;

import com.example.learning_backend.assessment.enums.AssessmentCompositionMode;
import com.example.learning_backend.assessment.enums.AssessmentStatus;
import com.example.learning_backend.assessment.enums.AssessmentType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssessmentResponse(
    Long id,
    Long courseId,
    Long lessonId,
    String title,
    String description,
    AssessmentType type,
    AssessmentStatus status,
    AssessmentCompositionMode compositionMode,
    LocalDateTime availableFrom,
    LocalDateTime availableUntil,
    Integer timeLimitMinutes,
    Integer maxAttempts,
    BigDecimal passingScore,
    Boolean shuffleQuestions,
    Boolean shuffleOptions,
    Boolean showAnswersAfterSubmit
) {
}



