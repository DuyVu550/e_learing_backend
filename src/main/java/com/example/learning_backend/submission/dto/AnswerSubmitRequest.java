package com.example.learning_backend.submission.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AnswerSubmitRequest(
    @NotNull Long questionId,
    String answerText,
    Set<Long> selectedOptionIds
) {
}
