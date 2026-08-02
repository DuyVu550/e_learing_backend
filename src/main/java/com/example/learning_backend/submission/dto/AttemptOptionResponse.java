package com.example.learning_backend.submission.dto;

// Deliberately omits the correct flag: this DTO is served to students while taking an exam.
public record AttemptOptionResponse(
    Long id,
    String optionText,
    Integer position
) {
}
