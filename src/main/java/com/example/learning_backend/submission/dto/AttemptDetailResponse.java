package com.example.learning_backend.submission.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AttemptDetailResponse(
    AssessmentAttemptResponse attempt,
    LocalDateTime deadline,
    List<AttemptQuestionResponse> questions
) {
}
