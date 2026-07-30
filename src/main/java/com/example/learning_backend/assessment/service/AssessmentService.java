package com.example.learning_backend.assessment.service;

import com.example.learning_backend.assessment.entity.Assessment;
import com.example.learning_backend.assessment.repository.AssessmentRepository;
import com.example.learning_backend.assessment.dto.AssessmentCreateRequest;
import com.example.learning_backend.assessment.dto.AssessmentResponse;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.entity.Lesson;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.course.repository.LessonRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;

    public AssessmentService(
        AssessmentRepository assessmentRepository,
        CourseRepository courseRepository,
        LessonRepository lessonRepository
    ) {
        this.assessmentRepository = assessmentRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> findByCourse(Long courseId) {
        return assessmentRepository.findByCourseId(courseId, Pageable.unpaged())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AssessmentResponse findById(Long id) {
        return assessmentRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + id));
    }

    public AssessmentResponse create(Long courseId, AssessmentCreateRequest request) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        Lesson lesson = null;
        if (request.lessonId() != null) {
            lesson = lessonRepository.findById(request.lessonId())
                .orElseThrow(() -> new IllegalArgumentException("Lesson not found: " + request.lessonId()));
        }

        Assessment assessment = new Assessment();
        assessment.setCourse(course);
        assessment.setLesson(lesson);
        assessment.setTitle(request.title());
        assessment.setDescription(request.description());
        assessment.setType(request.type());
        assessment.setTimeLimitMinutes(request.timeLimitMinutes());
        assessment.setMaxAttempts(request.maxAttempts());
        assessment.setPassingScore(request.passingScore());
        return toResponse(assessmentRepository.save(assessment));
    }

    private AssessmentResponse toResponse(Assessment assessment) {
        return new AssessmentResponse(
            assessment.getId(),
            assessment.getCourse() != null ? assessment.getCourse().getId() : null,
            assessment.getLesson() != null ? assessment.getLesson().getId() : null,
            assessment.getTitle(),
            assessment.getDescription(),
            assessment.getType(),
            assessment.getStatus(),
            assessment.getTimeLimitMinutes(),
            assessment.getMaxAttempts(),
            assessment.getPassingScore()
        );
    }
}



