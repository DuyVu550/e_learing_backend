package com.example.learning_backend.enrollment.entity;

import com.example.learning_backend.enrollment.enums.LessonProgressStatus;

import com.example.learning_backend.course.entity.Lesson;
import com.example.learning_backend.common.entity.BaseEntity;
import com.example.learning_backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lesson_progress")
public class LessonProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LessonProgressStatus status = LessonProgressStatus.NOT_STARTED;

    @Column(name = "last_position_seconds")
    private Integer lastPositionSeconds;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}



