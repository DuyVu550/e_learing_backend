package com.example.learning_backend.assessment.repository;

import com.example.learning_backend.assessment.entity.AssessmentQuestionSelection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentQuestionSelectionRepository extends JpaRepository<AssessmentQuestionSelection, Long> {

    List<AssessmentQuestionSelection> findByAssessmentIdOrderByPosition(Long assessmentId);

    boolean existsByAssessmentIdAndQuestionId(Long assessmentId, Long questionId);

    boolean existsByAssessmentIdAndPosition(Long assessmentId, Integer position);

    boolean existsByQuestionIdAndAssessmentStatus(Long questionId, com.example.learning_backend.assessment.enums.AssessmentStatus status);

    long countByAssessmentId(Long assessmentId);

    void deleteByAssessmentId(Long assessmentId);
}
