package com.example.learning_backend.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One ranked row. Carries {@code userId} and {@code fullName} only — a leaderboard must never
 * expose account emails.
 */
public record LeaderboardEntryResponse(
    Integer rank,
    Long userId,
    String fullName,
    BigDecimal score,
    BigDecimal maxScore,
    BigDecimal percentage,
    Long durationSeconds,
    LocalDateTime submittedAt
) {
}
