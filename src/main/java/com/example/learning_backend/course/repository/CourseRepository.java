package com.example.learning_backend.course.repository;

import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.enums.CourseStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);
}



