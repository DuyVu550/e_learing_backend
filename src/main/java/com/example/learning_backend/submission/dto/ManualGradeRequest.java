package com.example.learning_backend.submission.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ManualGradeRequest(
    @NotNull @DecimalMin(value = "0.0") BigDecimal score,
    String feedback
) {
}
