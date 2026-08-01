package com.example.learning_backend.assessment.controller;

import com.example.learning_backend.assessment.dto.QuestionRequest;
import com.example.learning_backend.assessment.dto.QuestionResponse;
import com.example.learning_backend.assessment.dto.QuestionTopicCreateRequest;
import com.example.learning_backend.assessment.dto.QuestionTopicResponse;
import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import com.example.learning_backend.assessment.service.QuestionBankService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    public QuestionBankController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @GetMapping("/courses/{courseId}/question-topics")
    public List<QuestionTopicResponse> findTopics(@PathVariable Long courseId) {
        return questionBankService.findTopics(courseId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/courses/{courseId}/question-topics")
    public QuestionTopicResponse createTopic(
        @PathVariable Long courseId,
        @Valid @RequestBody QuestionTopicCreateRequest request,
        Authentication authentication
    ) {
        return questionBankService.createTopic(courseId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PatchMapping("/question-topics/{topicId}")
    public QuestionTopicResponse updateTopic(
        @PathVariable Long topicId,
        @Valid @RequestBody QuestionTopicCreateRequest request,
        Authentication authentication
    ) {
        return questionBankService.updateTopic(topicId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @DeleteMapping("/question-topics/{topicId}")
    public void deleteTopic(@PathVariable Long topicId, Authentication authentication) {
        questionBankService.deleteTopic(topicId, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @GetMapping("/courses/{courseId}/questions")
    public List<QuestionResponse> findQuestions(
        @PathVariable Long courseId,
        @RequestParam(required = false) Long topicId,
        @RequestParam(required = false) QuestionDifficulty difficulty,
        @RequestParam(required = false) QuestionType type
    ) {
        return questionBankService.findQuestions(courseId, topicId, difficulty, type);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @GetMapping("/questions/{questionId}")
    public QuestionResponse findQuestion(@PathVariable Long questionId) {
        return questionBankService.findQuestion(questionId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/courses/{courseId}/questions")
    public QuestionResponse createQuestion(
        @PathVariable Long courseId,
        @Valid @RequestBody QuestionRequest request,
        Authentication authentication
    ) {
        return questionBankService.createQuestion(courseId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PatchMapping("/questions/{questionId}")
    public QuestionResponse updateQuestion(
        @PathVariable Long questionId,
        @Valid @RequestBody QuestionRequest request,
        Authentication authentication
    ) {
        return questionBankService.updateQuestion(questionId, request, authentication);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @DeleteMapping("/questions/{questionId}")
    public void deleteQuestion(@PathVariable Long questionId, Authentication authentication) {
        questionBankService.deleteQuestion(questionId, authentication);
    }
}
