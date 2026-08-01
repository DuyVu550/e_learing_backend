package com.example.learning_backend.assessment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AssessmentQuestionSelectionUpdateRequest(
    @Positive Integer position,
    @DecimalMin(value = "0.01") BigDecimal points
) {
}
