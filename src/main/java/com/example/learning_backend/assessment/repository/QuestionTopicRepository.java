package com.example.learning_backend.assessment.repository;

import com.example.learning_backend.assessment.entity.QuestionTopic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTopicRepository extends JpaRepository<QuestionTopic, Long> {

    List<QuestionTopic> findByCourseIdOrderByName(Long courseId);

    boolean existsByCourseIdAndNameIgnoreCase(Long courseId, String name);
}
