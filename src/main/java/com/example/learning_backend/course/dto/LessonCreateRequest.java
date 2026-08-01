package com.example.learning_backend.course.dto;

import com.example.learning_backend.course.enums.LessonContentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LessonCreateRequest(
    @NotBlank @Size(max = 255) String title,
    @NotNull LessonContentType contentType,
    String content,
    @Size(max = 500) String videoUrl,
    @Size(max = 500) String documentUrl,
    @Min(0) Integer durationSeconds,
    @NotNull @Min(0) Integer position,
    Boolean preview
) {
}
