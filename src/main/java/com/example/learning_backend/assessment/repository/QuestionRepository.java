package com.example.learning_backend.assessment.repository;

import com.example.learning_backend.assessment.entity.Question;
import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("""
        select q from Question q
        where q.course.id = :courseId
          and (:topicId is null or q.topic.id = :topicId)
          and (:difficulty is null or q.difficulty = :difficulty)
          and (:type is null or q.type = :type)
        order by q.id
        """)
    List<Question> findByCourseFilters(
        @Param("courseId") Long courseId,
        @Param("topicId") Long topicId,
        @Param("difficulty") QuestionDifficulty difficulty,
        @Param("type") QuestionType type
    );

    boolean existsByTopicId(Long topicId);
}




