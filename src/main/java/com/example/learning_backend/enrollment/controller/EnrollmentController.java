package com.example.learning_backend.enrollment.controller;

import com.example.learning_backend.enrollment.dto.CourseProgressResponse;
import com.example.learning_backend.enrollment.dto.EnrollmentResponse;
import com.example.learning_backend.enrollment.dto.LessonProgressRequest;
import com.example.learning_backend.enrollment.dto.LessonProgressResponse;
import com.example.learning_backend.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping("/courses/{courseId}/enroll")
    public EnrollmentResponse enroll(Authentication authentication, @PathVariable Long courseId) {
        return enrollmentService.enroll(authentication.getName(), courseId);
    }

    @GetMapping("/enrollments/me")
    public List<EnrollmentResponse> myEnrollments(Authentication authentication) {
        return enrollmentService.myEnrollments(authentication.getName());
    }

    @PatchMapping("/lessons/{lessonId}/progress")
    public LessonProgressResponse updateProgress(
        Authentication authentication,
        @PathVariable Long lessonId,
        @Valid @RequestBody LessonProgressRequest request
    ) {
        return enrollmentService.updateProgress(authentication.getName(), lessonId, request);
    }

    @GetMapping("/courses/{courseId}/progress")
    public CourseProgressResponse getCourseProgress(Authentication authentication, @PathVariable Long courseId) {
        return enrollmentService.getCourseProgress(authentication.getName(), courseId);
    }
}
