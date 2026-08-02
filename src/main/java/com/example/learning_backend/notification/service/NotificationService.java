package com.example.learning_backend.notification.service;

import com.example.learning_backend.notification.dto.NotificationResponse;
import com.example.learning_backend.notification.entity.Notification;
import com.example.learning_backend.notification.enums.NotificationType;
import com.example.learning_backend.notification.repository.NotificationRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications. Producers call {@link #notify} inside their own transaction, so a
 * notification can never outlive the action that caused it.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public void notify(User recipient, NotificationType type, String title, String message, Long referenceId) {
        if (recipient == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setReferenceId(referenceId);
        notificationRepository.save(notification);
    }

    /** Skips the actor so nobody is notified about their own action. */
    public void notifyOthers(User recipient, User actor, NotificationType type, String title, String message, Long referenceId) {
        if (recipient == null || (actor != null && recipient.getId().equals(actor.getId()))) {
            return;
        }
        notify(recipient, type, title, message, referenceId);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> myNotifications(String email, boolean unreadOnly) {
        User user = requireUser(email);
        List<Notification> notifications = unreadOnly
            ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId())
            : notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return notifications.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String email) {
        return notificationRepository.countByUserIdAndReadFalse(requireUser(email).getId());
    }

    public NotificationResponse markRead(String email, Long notificationId) {
        User user = requireUser(email);
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        markRead(notification);
        return toResponse(notification);
    }

    public long markAllRead(String email) {
        User user = requireUser(email);
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        unread.forEach(this::markRead);
        return unread.size();
    }

    private void markRead(Notification notification) {
        if (Boolean.TRUE.equals(notification.getRead())) {
            return;
        }
        notification.setRead(Boolean.TRUE);
        notification.setReadAt(LocalDateTime.now());
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getReferenceId(),
            notification.getRead(),
            notification.getReadAt(),
            notification.getCreatedAt()
        );
    }
}
