package com.example.learning_backend.submission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AssessmentSubmitRequest(
    @NotEmpty List<@Valid AnswerSubmitRequest> answers
) {
}
