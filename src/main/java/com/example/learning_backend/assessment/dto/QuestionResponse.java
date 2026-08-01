package com.example.learning_backend.assessment.dto;

import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import java.math.BigDecimal;
import java.util.List;

public record QuestionResponse(
    Long id,
    Long courseId,
    Long topicId,
    String questionText,
    QuestionType type,
    QuestionDifficulty difficulty,
    BigDecimal points,
    String expectedAnswer,
    String explanation,
    List<QuestionOptionResponse> options
) {
}
