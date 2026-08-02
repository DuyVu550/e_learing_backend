package com.example.learning_backend.analytics.dto;

import java.math.BigDecimal;

/**
 * One row of the score distribution (phổ điểm) for an assessment.
 */
public record ScoreDistributionResponse(
    String band,
    String label,
    BigDecimal minPercentage,
    BigDecimal maxPercentage,
    long attemptCount,
    BigDecimal share
) {
}
