package com.example.learning_backend.analytics.dto;

import java.math.BigDecimal;

/**
 * One ranked row of the system-wide leaderboard, aggregated across every graded assessment the
 * student has taken. Carries no email, same as {@link LeaderboardEntryResponse}.
 */
public record GlobalLeaderboardEntryResponse(
    Integer rank,
    Long userId,
    String fullName,
    long assessmentCount,
    BigDecimal totalScore,
    BigDecimal totalMaxScore,
    BigDecimal percentage,
    Long totalDurationSeconds
) {
}
