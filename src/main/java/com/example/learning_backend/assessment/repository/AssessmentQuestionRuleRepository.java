package com.example.learning_backend.assessment.repository;

import com.example.learning_backend.assessment.entity.AssessmentQuestionRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentQuestionRuleRepository extends JpaRepository<AssessmentQuestionRule, Long> {

    List<AssessmentQuestionRule> findByAssessmentIdOrderByPosition(Long assessmentId);

    boolean existsByAssessmentIdAndPosition(Long assessmentId, Integer position);

    long countByAssessmentId(Long assessmentId);
}
