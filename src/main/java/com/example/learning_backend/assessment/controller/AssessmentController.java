package com.example.learning_backend.assessment.controller;

import com.example.learning_backend.assessment.dto.AssessmentCreateRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionRuleRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionRuleResponse;
import com.example.learning_backend.assessment.dto.AssessmentQuestionRuleUpdateRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionSelectionRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionSelectionResponse;
import com.example.learning_backend.assessment.dto.AssessmentQuestionSelectionUpdateRequest;
import com.example.learning_backend.assessment.dto.AssessmentResponse;
import com.example.learning_backend.assessment.dto.AssessmentUpdateRequest;
import com.example.learning_backend.assessment.service.AssessmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/courses/{courseId}/assessments")
    public AssessmentResponse create(
        @PathVariable Long courseId,
        @Valid @RequestBody AssessmentCreateRequest request,
        Authentication authentication
    ) {
        return assessmentService.create(courseId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PatchMapping("/assessments/{assessmentId}")
    public AssessmentResponse update(
        @PathVariable Long assessmentId,
        @Valid @RequestBody AssessmentUpdateRequest request,
        Authentication authentication
    ) {
        return assessmentService.update(assessmentId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @GetMapping("/assessments/{assessmentId}/question-selections")
    public List<AssessmentQuestionSelectionResponse> findSelections(@PathVariable Long assessmentId) {
        return assessmentService.findSelections(assessmentId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/assessments/{assessmentId}/question-selections")
    public AssessmentQuestionSelectionResponse addSelection(
        @PathVariable Long assessmentId,
        @Valid @RequestBody AssessmentQuestionSelectionRequest request,
        Authentication authentication
    ) {
        return assessmentService.addSelection(assessmentId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PatchMapping("/assessment-question-selections/{selectionId}")
    public AssessmentQuestionSelectionResponse updateSelection(
        @PathVariable Long selectionId,
        @Valid @RequestBody AssessmentQuestionSelectionUpdateRequest request,
        Authentication authentication
    ) {
        return assessmentService.updateSelection(selectionId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @DeleteMapping("/assessment-question-selections/{selectionId}")
    public void deleteSelection(@PathVariable Long selectionId, Authentication authentication) {
        assessmentService.deleteSelection(selectionId, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @GetMapping("/assessments/{assessmentId}/question-rules")
    public List<AssessmentQuestionRuleResponse> findRules(@PathVariable Long assessmentId) {
        return assessmentService.findRules(assessmentId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/assessments/{assessmentId}/question-rules")
    public AssessmentQuestionRuleResponse addRule(
        @PathVariable Long assessmentId,
        @Valid @RequestBody AssessmentQuestionRuleRequest request,
        Authentication authentication
    ) {
        return assessmentService.addRule(assessmentId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PatchMapping("/assessment-question-rules/{ruleId}")
    public AssessmentQuestionRuleResponse updateRule(
        @PathVariable Long ruleId,
        @Valid @RequestBody AssessmentQuestionRuleUpdateRequest request,
        Authentication authentication
    ) {
        return assessmentService.updateRule(ruleId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @DeleteMapping("/assessment-question-rules/{ruleId}")
    public void deleteRule(@PathVariable Long ruleId, Authentication authentication) {
        assessmentService.deleteRule(ruleId, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/assessments/{assessmentId}/question-selections/generate")
    public List<AssessmentQuestionSelectionResponse> generateSelections(
        @PathVariable Long assessmentId,
        Authentication authentication
    ) {
        return assessmentService.generateSelections(assessmentId, authentication);
    }
}
