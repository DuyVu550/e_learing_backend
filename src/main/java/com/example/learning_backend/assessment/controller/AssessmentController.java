package com.example.learning_backend.assessment.controller;

import com.example.learning_backend.assessment.service.AssessmentService;
import com.example.learning_backend.assessment.dto.AssessmentCreateRequest;
import com.example.learning_backend.assessment.dto.AssessmentResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/courses/{courseId}/assessments")
    public List<AssessmentResponse> findByCourse(@PathVariable Long courseId) {
        return assessmentService.findByCourse(courseId);
    }

    @GetMapping("/assessments/{id}")
    public AssessmentResponse findById(@PathVariable Long id) {
        return assessmentService.findById(id);
    }

    @PostMapping("/courses/{courseId}/assessments")
    public AssessmentResponse create(
        @PathVariable Long courseId,
        @Valid @RequestBody AssessmentCreateRequest request
    ) {
        return assessmentService.create(courseId, request);
    }
}



