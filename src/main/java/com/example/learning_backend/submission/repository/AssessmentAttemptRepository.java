package com.example.learning_backend.submission.repository;

import com.example.learning_backend.submission.entity.AssessmentAttempt;
import com.example.learning_backend.submission.enums.AttemptStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {

    long countByAssessmentIdAndUserId(Long assessmentId, Long userId);

    Page<AssessmentAttempt> findByUserId(Long userId, Pageable pageable);

    Optional<AssessmentAttempt> findFirstByAssessmentIdAndUserIdAndStatusOrderByStartedAtDesc(
        Long assessmentId,
        Long userId,
        AttemptStatus status
    );

    Optional<AssessmentAttempt> findByIdAndUserId(Long id, Long userId);

    Page<AssessmentAttempt> findByAssessmentId(Long assessmentId, Pageable pageable);

    List<AssessmentAttempt> findByAssessmentIdAndStatus(Long assessmentId, AttemptStatus status);

    List<AssessmentAttempt> findByStatus(AttemptStatus status);
}
