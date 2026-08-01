package com.example.learning_backend.assessment.dto;

import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import java.math.BigDecimal;

public record AssessmentQuestionRuleResponse(
    Long id,
    Long assessmentId,
    Long topicId,
    QuestionDifficulty difficulty,
    QuestionType questionType,
    Integer questionCount,
    BigDecimal points,
    Integer position
) {
}
