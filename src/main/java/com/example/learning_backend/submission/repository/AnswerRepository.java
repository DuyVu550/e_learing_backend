package com.example.learning_backend.submission.repository;

import com.example.learning_backend.analytics.dto.QuestionStatRow;
import com.example.learning_backend.submission.entity.Answer;
import com.example.learning_backend.submission.enums.AttemptStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByAttemptId(Long attemptId);

    Optional<Answer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    /**
     * Per-question answered/correct counts across attempts in the given status. Questions nobody
     * answered produce no row at all, so callers must treat a missing question as zero answers
     * rather than as a zero wrong rate. The {@code case when} keeps ungraded answers counted in the
     * denominator instead of dropping them.
     */
    @Query("""
        select new com.example.learning_backend.analytics.dto.QuestionStatRow(
            a.question.id,
            count(a),
            sum(case when a.correct = true then 1L else 0L end))
        from Answer a
        where a.attempt.assessment.id = :assessmentId
          and a.attempt.status = :status
        group by a.question.id
        """)
    List<QuestionStatRow> findQuestionStats(
        @Param("assessmentId") Long assessmentId,
        @Param("status") AttemptStatus status
    );
}
