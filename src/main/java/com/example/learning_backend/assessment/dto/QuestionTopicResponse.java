package com.example.learning_backend.assessment.dto;

public record QuestionTopicResponse(
    Long id,
    Long courseId,
    String name,
    String description
) {
}
