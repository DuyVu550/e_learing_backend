package com.example.learning_backend.course.dto;

import com.example.learning_backend.course.enums.LessonContentType;

public record LessonResponse(
    Long id,
    Long sectionId,
    String title,
    LessonContentType contentType,
    String content,
    String videoUrl,
    String documentUrl,
    Integer durationSeconds,
    Integer position,
    Boolean preview
) {
}
