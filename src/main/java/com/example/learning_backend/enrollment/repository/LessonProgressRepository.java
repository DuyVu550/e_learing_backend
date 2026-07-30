package com.example.learning_backend.enrollment.repository;

import com.example.learning_backend.enrollment.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
}



