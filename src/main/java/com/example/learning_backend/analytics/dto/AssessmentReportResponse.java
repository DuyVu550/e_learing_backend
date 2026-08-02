package com.example.learning_backend.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Instructor report for one assessment: headline numbers, score distribution and per-question
 * difficulty analysis. Only {@code GRADED} attempts are counted — attempts still awaiting manual
 * essay grading have an incomplete score and would skew every figure here.
 */
public record AssessmentReportResponse(
    Long assessmentId,
    String assessmentTitle,
    long gradedAttemptCount,
    long pendingGradingCount,
    long participantCount,
    BigDecimal averageScore,
    BigDecimal averagePercentage,
    BigDecimal highestScore,
    BigDecimal lowestScore,
    long passedCount,
    BigDecimal passRate,
    List<ScoreDistributionResponse> distribution,
    List<QuestionAnalysisResponse> questions
) {
}
