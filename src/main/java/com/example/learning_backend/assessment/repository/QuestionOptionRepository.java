package com.example.learning_backend.assessment.repository;

import com.example.learning_backend.assessment.entity.QuestionOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    List<QuestionOption> findByQuestionIdOrderByPosition(Long questionId);

    List<QuestionOption> findByQuestionId(Long questionId);

    List<QuestionOption> findByIdIn(Collection<Long> ids);

    void deleteByQuestionId(Long questionId);
}



