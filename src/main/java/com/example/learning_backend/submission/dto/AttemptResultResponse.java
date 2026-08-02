package com.example.learning_backend.submission.dto;

import java.util.List;

public record AttemptResultResponse(
    AssessmentAttemptResponse attempt,
    Boolean answersRevealed,
    Boolean awaitingManualGrading,
    List<AnswerResultResponse> answers
) {
}
