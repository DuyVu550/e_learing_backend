package com.example.learning_backend.analytics.dto;

/**
 * Raw aggregate row from the per-question grouping query. Counts arrive as {@code Long} because
 * that is what JPQL {@code count}/{@code sum} return.
 */
public record QuestionStatRow(Long questionId, Long answeredCount, Long correctCount) {
}
