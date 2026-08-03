package com.example.learning_backend.course.dto;

import com.example.learning_backend.course.enums.CourseLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CourseCreateRequest(
    @NotBlank @Size(max = 180) String slug,
    @NotBlank @Size(max = 255) String title,
    String description,
    CourseLevel level,
    @DecimalMin(value = "0.0") BigDecimal price,
    @NotNull Long instructorId
) {
}



