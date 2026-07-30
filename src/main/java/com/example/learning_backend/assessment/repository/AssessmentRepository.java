package com.example.learning_backend.assessment.repository;

import com.example.learning_backend.assessment.entity.Assessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    Page<Assessment> findByCourseId(Long courseId, Pageable pageable);
}



