package com.example.learning_backend.assessment.dto;

import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AssessmentQuestionRuleUpdateRequest(
    Long topicId,
    QuestionDifficulty difficulty,
    QuestionType questionType,
    @Positive Integer questionCount,
    @DecimalMin(value = "0.01") BigDecimal points,
    @Positive Integer position
) {
}
