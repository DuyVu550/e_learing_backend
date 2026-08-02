package com.example.learning_backend.enrollment.service;

import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import org.springframework.stereotype.Component;

/**
 * Central rule for "is this user an active member of this course?". Shared so the check cannot
 * drift between the services that enforce it.
 */
@Component
public class EnrollmentAccessPolicy {

    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentAccessPolicy(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    public Enrollment requireActive(Long userId, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
            .orElseThrow(() -> new IllegalArgumentException("User is not enrolled in course: " + courseId));
        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Enrollment is cancelled for course: " + courseId);
        }
        return enrollment;
    }
}
