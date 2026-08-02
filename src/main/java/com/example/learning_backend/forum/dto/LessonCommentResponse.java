package com.example.learning_backend.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A top-level question with its replies, or (when nested inside {@code replies}) a single reply
 * whose own {@code replies} list is always empty — threading is one level deep.
 */
public record LessonCommentResponse(
    Long id,
    Long lessonId,
    Long parentId,
    Long authorId,
    String authorName,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<LessonCommentResponse> replies
) {
}
