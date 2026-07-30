package com.example.learning_backend.submission.repository;

import com.example.learning_backend.submission.entity.AssessmentAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {

    long countByAssessmentIdAndUserId(Long assessmentId, Long userId);

    Page<AssessmentAttempt> findByUserId(Long userId, Pageable pageable);
}



