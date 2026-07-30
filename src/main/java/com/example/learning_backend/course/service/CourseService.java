package com.example.learning_backend.course.service;

import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.course.dto.CourseCreateRequest;
import com.example.learning_backend.course.dto.CourseResponse;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseService(CourseRepository courseRepository, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> findAll() {
        return courseRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse findById(Long id) {
        return courseRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
    }

    public CourseResponse create(CourseCreateRequest request) {
        User instructor = userRepository.findById(request.instructorId())
            .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + request.instructorId()));

        Course course = new Course();
        course.setSlug(request.slug());
        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setLevel(request.level());
        course.setInstructor(instructor);
        return toResponse(courseRepository.save(course));
    }

    private CourseResponse toResponse(Course course) {
        return new CourseResponse(
            course.getId(),
            course.getSlug(),
            course.getTitle(),
            course.getDescription(),
            course.getLevel(),
            course.getStatus(),
            course.getInstructor() != null ? course.getInstructor().getId() : null
        );
    }
}



