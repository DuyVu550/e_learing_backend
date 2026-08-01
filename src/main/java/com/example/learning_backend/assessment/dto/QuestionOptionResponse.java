package com.example.learning_backend.assessment.dto;

public record QuestionOptionResponse(
    Long id,
    String optionText,
    Boolean correct,
    Integer position
) {
}
