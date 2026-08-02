package com.example.learning_backend.submission.controller;

import com.example.learning_backend.submission.dto.AnswerResultResponse;
import com.example.learning_backend.submission.dto.AssessmentAttemptResponse;
import com.example.learning_backend.submission.dto.AssessmentSubmitRequest;
import com.example.learning_backend.submission.dto.AttemptDetailResponse;
import com.example.learning_backend.submission.dto.AttemptResultResponse;
import com.example.learning_backend.submission.dto.ManualGradeRequest;
import com.example.learning_backend.submission.service.SubmissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/assessments/{assessmentId}/attempts")
    public AttemptDetailResponse startOrResume(Authentication authentication, @PathVariable Long assessmentId) {
        return submissionService.startOrResume(authentication.getName(), assessmentId);
    }

    @GetMapping("/attempts/me")
    public List<AssessmentAttemptResponse> myAttempts(Authentication authentication) {
        return submissionService.myAttempts(authentication.getName());
    }

    @GetMapping("/attempts/{attemptId}")
    public AttemptDetailResponse getAttempt(Authentication authentication, @PathVariable Long attemptId) {
        return submissionService.getAttempt(authentication.getName(), attemptId);
    }

    @PutMapping("/attempts/{attemptId}/answers")
    public AttemptDetailResponse saveDraft(
        Authentication authentication,
        @PathVariable Long attemptId,
        @Valid @RequestBody AssessmentSubmitRequest request
    ) {
        return submissionService.saveDraft(authentication.getName(), attemptId, request);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public AttemptResultResponse submit(
        Authentication authentication,
        @PathVariable Long attemptId,
        @Valid @RequestBody AssessmentSubmitRequest request
    ) {
        return submissionService.submit(authentication.getName(), attemptId, request);
    }

    @GetMapping("/attempts/{attemptId}/result")
    public AttemptResultResponse getResult(Authentication authentication, @PathVariable Long attemptId) {
        return submissionService.getResult(authentication.getName(), attemptId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @GetMapping("/assessments/{assessmentId}/attempts")
    public List<AssessmentAttemptResponse> findByAssessment(
        @PathVariable Long assessmentId,
        Authentication authentication
    ) {
        return submissionService.findByAssessment(assessmentId, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PatchMapping("/answers/{answerId}/grade")
    public AnswerResultResponse gradeAnswer(
        @PathVariable Long answerId,
        @Valid @RequestBody ManualGradeRequest request,
        Authentication authentication
    ) {
        return submissionService.gradeAnswer(answerId, request, authentication);
    }
}
