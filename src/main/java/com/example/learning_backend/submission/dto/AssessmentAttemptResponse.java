package com.example.learning_backend.submission.dto;

import com.example.learning_backend.submission.enums.AttemptStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssessmentAttemptResponse(
    Long id,
    Long assessmentId,
    Integer attemptNo,
    AttemptStatus status,
    LocalDateTime startedAt,
    LocalDateTime submittedAt,
    BigDecimal score,
    BigDecimal maxScore,
    Boolean passed
) {
}
