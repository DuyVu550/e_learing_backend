package com.example.learning_backend.notification.dto;

import com.example.learning_backend.notification.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    NotificationType type,
    String title,
    String message,
    Long referenceId,
    Boolean read,
    LocalDateTime readAt,
    LocalDateTime createdAt
) {
}
