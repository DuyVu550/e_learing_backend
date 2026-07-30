package com.example.learning_backend.course.dto;

import com.example.learning_backend.course.enums.CourseLevel;
import com.example.learning_backend.course.enums.CourseStatus;

public record CourseResponse(
    Long id,
    String slug,
    String title,
    String description,
    CourseLevel level,
    CourseStatus status,
    Long instructorId
) {
}



