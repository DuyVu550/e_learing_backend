package com.example.learning_backend.assessment.dto;

import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record QuestionRequest(
    Long topicId,
    @NotBlank String questionText,
    @NotNull QuestionType type,
    @NotNull QuestionDifficulty difficulty,
    @NotNull @DecimalMin(value = "0.01") BigDecimal points,
    String expectedAnswer,
    String explanation,
    List<@Valid QuestionOptionRequest> options
) {
}
