package com.example.learning_backend.course.controller;

import com.example.learning_backend.course.service.CourseService;
import com.example.learning_backend.course.dto.CourseCreateRequest;
import com.example.learning_backend.course.dto.CourseResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public List<CourseResponse> findAll() {
        return courseService.findAll();
    }

    @GetMapping("/{id}")
    public CourseResponse findById(@PathVariable Long id) {
        return courseService.findById(id);
    }

    @PostMapping
    public CourseResponse create(@Valid @RequestBody CourseCreateRequest request) {
        return courseService.create(request);
    }
}



