package com.example.learning_backend.assessment.dto;

import java.math.BigDecimal;

public record AssessmentQuestionSelectionResponse(
    Long id,
    Long assessmentId,
    Integer position,
    BigDecimal points,
    QuestionResponse question
) {
}
