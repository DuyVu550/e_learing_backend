package com.example.learning_backend.assessment.repository;

import com.example.learning_backend.assessment.entity.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByAssessmentIdOrderByPosition(Long assessmentId);
}



