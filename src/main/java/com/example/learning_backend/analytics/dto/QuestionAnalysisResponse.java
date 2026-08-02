package com.example.learning_backend.analytics.dto;

import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import java.math.BigDecimal;

/**
 * Difficulty analysis for a single question. {@code answeredCount} is reported alongside
 * {@code wrongRate} so a question nobody attempted is visibly unanswered rather than looking like
 * a question everybody got right.
 */
public record QuestionAnalysisResponse(
    Long questionId,
    Integer position,
    String questionText,
    QuestionType type,
    QuestionDifficulty difficulty,
    long answeredCount,
    long correctCount,
    long wrongCount,
    BigDecimal wrongRate
) {
}
