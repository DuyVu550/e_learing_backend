package com.example.learning_backend.enrollment.service;

import com.example.learning_backend.course.entity.Lesson;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.course.repository.LessonRepository;
import com.example.learning_backend.enrollment.dto.EnrollmentResponse;
import com.example.learning_backend.enrollment.dto.LessonProgressRequest;
import com.example.learning_backend.enrollment.dto.LessonProgressResponse;
import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.entity.LessonProgress;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.enums.LessonProgressStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import com.example.learning_backend.enrollment.repository.LessonProgressRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;

    public EnrollmentService(
        EnrollmentRepository enrollmentRepository,
        LessonProgressRepository lessonProgressRepository,
        CourseRepository courseRepository,
        LessonRepository lessonRepository,
        UserRepository userRepository
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
    }

    public EnrollmentResponse enroll(String email, Long courseId) {
        User user = requireUser(email);
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        return enrollmentRepository.findByUserIdAndCourseId(user.getId(), courseId)
            .map(this::toResponse)
            .orElseGet(() -> {
                Enrollment enrollment = new Enrollment();
                enrollment.setUser(user);
                enrollment.setCourse(course);
                enrollment.setStatus(EnrollmentStatus.ACTIVE);
                enrollment.setEnrolledAt(LocalDateTime.now());
                return toResponse(enrollmentRepository.save(enrollment));
            });
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> myEnrollments(String email) {
        User user = requireUser(email);
        return enrollmentRepository.findByUserId(user.getId(), Pageable.unpaged())
            .stream().map(this::toResponse).toList();
    }

    public LessonProgressResponse updateProgress(String email, Long lessonId, LessonProgressRequest request) {
        User user = requireUser(email);
        Lesson lesson = lessonRepository.findById(lessonId)
            .orElseThrow(() -> new IllegalArgumentException("Lesson not found: " + lessonId));

        LessonProgress progress = lessonProgressRepository.findByUserIdAndLessonId(user.getId(), lessonId)
            .orElseGet(() -> {
                LessonProgress created = new LessonProgress();
                created.setUser(user);
                created.setLesson(lesson);
                return created;
            });
        progress.setStatus(request.status());
        progress.setLastPositionSeconds(request.lastPositionSeconds());
        if (request.status() == LessonProgressStatus.COMPLETED) {
            progress.setCompletedAt(LocalDateTime.now());
        }
        return toResponse(lessonProgressRepository.save(progress));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(
            enrollment.getId(),
            enrollment.getCourse() != null ? enrollment.getCourse().getId() : null,
            enrollment.getStatus(),
            enrollment.getEnrolledAt(),
            enrollment.getCompletedAt()
        );
    }

    private LessonProgressResponse toResponse(LessonProgress progress) {
        return new LessonProgressResponse(
            progress.getId(),
            progress.getLesson() != null ? progress.getLesson().getId() : null,
            progress.getStatus(),
            progress.getLastPositionSeconds(),
            progress.getCompletedAt()
        );
    }
}
