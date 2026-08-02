package com.example.learning_backend.submission.dto;

import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

// Deliberately omits expectedAnswer and explanation: this DTO is served to students while taking an exam.
public record AttemptQuestionResponse(
    Long questionId,
    Integer position,
    BigDecimal points,
    String questionText,
    QuestionType type,
    QuestionDifficulty difficulty,
    List<AttemptOptionResponse> options,
    String answerText,
    Set<Long> selectedOptionIds,
    Boolean flagged
) {
}
