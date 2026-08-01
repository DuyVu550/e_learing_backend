package com.example.learning_backend.course.repository;

import com.example.learning_backend.course.entity.Lesson;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findBySectionIdOrderByPositionAsc(Long sectionId);

    long countBySectionCourseId(Long courseId);
}



