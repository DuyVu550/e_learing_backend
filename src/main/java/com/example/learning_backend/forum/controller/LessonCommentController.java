package com.example.learning_backend.forum.controller;

import com.example.learning_backend.forum.dto.LessonCommentRequest;
import com.example.learning_backend.forum.dto.LessonCommentResponse;
import com.example.learning_backend.forum.dto.LessonCommentUpdateRequest;
import com.example.learning_backend.forum.service.LessonCommentService;
import jakarta.validation.Valid;
import java.util.List;
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
public class LessonCommentController {

    private final LessonCommentService lessonCommentService;

    public LessonCommentController(LessonCommentService lessonCommentService) {
        this.lessonCommentService = lessonCommentService;
    }

    @PostMapping("/lessons/{lessonId}/comments")
    public LessonCommentResponse create(
        @PathVariable Long lessonId,
        @Valid @RequestBody LessonCommentRequest request,
        Authentication authentication
    ) {
        return lessonCommentService.create(lessonId, request, authentication);
    }

    @GetMapping("/lessons/{lessonId}/comments")
    public List<LessonCommentResponse> findByLesson(@PathVariable Long lessonId, Authentication authentication) {
        return lessonCommentService.findByLesson(lessonId, authentication);
    }

    @PatchMapping("/comments/{commentId}")
    public LessonCommentResponse update(
        @PathVariable Long commentId,
        @Valid @RequestBody LessonCommentUpdateRequest request,
        Authentication authentication
    ) {
        return lessonCommentService.update(commentId, request, authentication);
    }

    @DeleteMapping("/comments/{commentId}")
    public void delete(@PathVariable Long commentId, Authentication authentication) {
        lessonCommentService.delete(commentId, authentication);
    }
}
