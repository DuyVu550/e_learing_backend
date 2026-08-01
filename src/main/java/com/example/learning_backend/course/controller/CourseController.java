package com.example.learning_backend.course.controller;

import com.example.learning_backend.course.dto.CourseCreateRequest;
import com.example.learning_backend.course.dto.CourseDetailResponse;
import com.example.learning_backend.course.dto.CourseResponse;
import com.example.learning_backend.course.dto.CourseSectionCreateRequest;
import com.example.learning_backend.course.dto.CourseSectionResponse;
import com.example.learning_backend.course.dto.LessonCreateRequest;
import com.example.learning_backend.course.dto.LessonResponse;
import com.example.learning_backend.course.service.CourseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/courses")
    public List<CourseResponse> findAll() {
        return courseService.findAll();
    }

    @GetMapping("/courses/{id}")
    public CourseResponse findById(@PathVariable Long id) {
        return courseService.findById(id);
    }

    @GetMapping("/courses/{id}/detail")
    public CourseDetailResponse findDetail(@PathVariable Long id) {
        return courseService.findDetail(id);
    }

    @GetMapping("/courses/{courseId}/sections")
    public List<CourseSectionResponse> findSections(@PathVariable Long courseId) {
        return courseService.findSections(courseId);
    }

    @GetMapping("/sections/{sectionId}/lessons")
    public List<LessonResponse> findLessons(@PathVariable Long sectionId) {
        return courseService.findLessons(sectionId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/courses")
    public CourseResponse create(@Valid @RequestBody CourseCreateRequest request) {
        return courseService.create(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/courses/{courseId}/sections")
    public CourseSectionResponse createSection(
        @PathVariable Long courseId,
        @Valid @RequestBody CourseSectionCreateRequest request
    ) {
        return courseService.createSection(courseId, request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @PostMapping("/sections/{sectionId}/lessons")
    public LessonResponse createLesson(
        @PathVariable Long sectionId,
        @Valid @RequestBody LessonCreateRequest request
    ) {
        return courseService.createLesson(sectionId, request);
    }
}
