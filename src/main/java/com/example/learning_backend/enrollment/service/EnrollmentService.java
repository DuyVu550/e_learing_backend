package com.example.learning_backend.enrollment.service;

import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.entity.Lesson;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.course.repository.LessonRepository;
import com.example.learning_backend.enrollment.dto.CourseProgressResponse;
import com.example.learning_backend.enrollment.dto.EnrollmentResponse;
import com.example.learning_backend.enrollment.dto.LessonProgressRequest;
import com.example.learning_backend.enrollment.dto.LessonProgressResponse;
import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.entity.LessonProgress;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.enums.LessonProgressStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import com.example.learning_backend.enrollment.repository.LessonProgressRepository;
import com.example.learning_backend.payment.enums.PaymentStatus;
import com.example.learning_backend.payment.repository.PaymentRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final PaymentRepository paymentRepository;
    private final EnrollmentAccessPolicy enrollmentAccessPolicy;

    public EnrollmentService(
        EnrollmentRepository enrollmentRepository,
        LessonProgressRepository lessonProgressRepository,
        CourseRepository courseRepository,
        LessonRepository lessonRepository,
        UserRepository userRepository,
        PaymentRepository paymentRepository,
        EnrollmentAccessPolicy enrollmentAccessPolicy
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.enrollmentAccessPolicy = enrollmentAccessPolicy;
    }

    public EnrollmentResponse enroll(String email, Long courseId) {
        User user = requireUser(email);
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        return enrollmentRepository.findByUserIdAndCourseId(user.getId(), courseId)
            .map(this::toResponse)
            .orElseGet(() -> {
                requirePaidIfNotFree(user.getId(), course);
                Enrollment enrollment = new Enrollment();
                enrollment.setUser(user);
                enrollment.setCourse(course);
                enrollment.setStatus(EnrollmentStatus.ACTIVE);
                enrollment.setEnrolledAt(LocalDateTime.now());
                return toResponse(enrollmentRepository.save(enrollment));
            });
    }

    /**
     * Free self-enrollment stops at the paywall. A paid course is unlocked by the payment webhook,
     * which creates the enrollment itself, so reaching here without a PAID payment means the caller
     * skipped checkout.
     */
    private void requirePaidIfNotFree(Long userId, Course course) {
        if (course.getPrice() == null || course.getPrice().signum() <= 0) {
            return;
        }
        boolean paid = paymentRepository
            .existsByUserIdAndCourseIdAndStatus(userId, course.getId(), PaymentStatus.PAID);
        if (!paid) {
            throw new IllegalArgumentException("Course requires payment: " + course.getId());
        }
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
        Long courseId = lesson.getSection().getCourse().getId();
        Enrollment enrollment = requireEnrollment(user.getId(), courseId);

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
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(LocalDateTime.now());
            }
        } else {
            progress.setCompletedAt(null);
        }

        LessonProgress saved = lessonProgressRepository.save(progress);
        syncEnrollmentCompletion(user.getId(), courseId, enrollment);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CourseProgressResponse getCourseProgress(String email, Long courseId) {
        User user = requireUser(email);
        courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        requireEnrollment(user.getId(), courseId);
        return buildCourseProgress(user.getId(), courseId);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private Enrollment requireEnrollment(Long userId, Long courseId) {
        return enrollmentAccessPolicy.requireActive(userId, courseId);
    }

    private void syncEnrollmentCompletion(Long userId, Long courseId, Enrollment enrollment) {
        CourseProgressResponse progress = buildCourseProgress(userId, courseId);
        if (progress.completed()) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            if (enrollment.getCompletedAt() == null) {
                enrollment.setCompletedAt(LocalDateTime.now());
            }
            return;
        }
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setCompletedAt(null);
    }

    private CourseProgressResponse buildCourseProgress(Long userId, Long courseId) {
        long totalLessons = lessonRepository.countBySectionCourseId(courseId);
        long completedLessons = lessonProgressRepository.countByUserIdAndStatusAndLessonSectionCourseId(
            userId,
            LessonProgressStatus.COMPLETED,
            courseId
        );
        BigDecimal percentage = totalLessons == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(completedLessons)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalLessons), 2, RoundingMode.HALF_UP);
        boolean completed = totalLessons > 0 && completedLessons == totalLessons;
        return new CourseProgressResponse(courseId, totalLessons, completedLessons, percentage, completed);
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
