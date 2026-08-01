package com.example.learning_backend.enrollment.repository;

import com.example.learning_backend.enrollment.entity.LessonProgress;
import com.example.learning_backend.enrollment.enums.LessonProgressStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, Long lessonId);

    Page<LessonProgress> findByUserId(Long userId, Pageable pageable);

    long countByUserIdAndStatusAndLessonSectionCourseId(
        Long userId,
        LessonProgressStatus status,
        Long courseId
    );
}



