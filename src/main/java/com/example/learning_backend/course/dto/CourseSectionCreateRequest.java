package com.example.learning_backend.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourseSectionCreateRequest(
    @NotBlank @Size(max = 255) String title,
    @NotNull @Min(0) Integer position
) {
}
