package com.example.learning_backend.course.dto;

import com.example.learning_backend.course.enums.CourseLevel;
import com.example.learning_backend.course.enums.CourseStatus;
import java.math.BigDecimal;
import java.util.List;

public record CourseDetailResponse(
    Long id,
    String slug,
    String title,
    String description,
    CourseLevel level,
    CourseStatus status,
    BigDecimal price,
    Long instructorId,
    List<CourseSectionResponse> sections
) {
}
