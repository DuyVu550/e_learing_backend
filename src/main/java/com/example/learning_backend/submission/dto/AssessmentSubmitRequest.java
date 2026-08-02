package com.example.learning_backend.submission.dto;

import jakarta.validation.Valid;
import java.util.List;

// ponytail: shared by auto-save and final submit; an empty list is valid when the student clears their last answer.
public record AssessmentSubmitRequest(
    List<@Valid AnswerSubmitRequest> answers
) {
}
