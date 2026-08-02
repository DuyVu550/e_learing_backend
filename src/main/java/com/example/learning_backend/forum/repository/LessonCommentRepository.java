package com.example.learning_backend.forum.repository;

import com.example.learning_backend.forum.entity.LessonComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonCommentRepository extends JpaRepository<LessonComment, Long> {

    List<LessonComment> findByLessonIdOrderByCreatedAtAsc(Long lessonId);

    List<LessonComment> findByParentIdOrderByCreatedAtAsc(Long parentId);
}
