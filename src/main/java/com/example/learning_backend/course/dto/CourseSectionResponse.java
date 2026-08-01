package com.example.learning_backend.course.dto;

import java.util.List;

public record CourseSectionResponse(
    Long id,
    Long courseId,
    String title,
    Integer position,
    List<LessonResponse> lessons
) {
}
