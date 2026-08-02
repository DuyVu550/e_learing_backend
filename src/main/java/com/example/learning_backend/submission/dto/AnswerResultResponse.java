package com.example.learning_backend.submission.dto;

import com.example.learning_backend.assessment.enums.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

// correctOptionIds, expectedAnswer and explanation stay null unless the assessment allows showing answers.
public record AnswerResultResponse(
    Long answerId,
    Long questionId,
    Integer position,
    String questionText,
    QuestionType type,
    BigDecimal points,
    BigDecimal score,
    Boolean correct,
    Boolean flagged,
    String answerText,
    Set<Long> selectedOptionIds,
    String feedback,
    List<AttemptOptionResponse> options,
    Set<Long> correctOptionIds,
    String expectedAnswer,
    String explanation
) {
}
