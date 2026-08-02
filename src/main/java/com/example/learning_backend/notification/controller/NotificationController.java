package com.example.learning_backend.notification.controller;

import com.example.learning_backend.notification.dto.NotificationResponse;
import com.example.learning_backend.notification.service.NotificationService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications/me")
    public List<NotificationResponse> myNotifications(
        Authentication authentication,
        @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return notificationService.myNotifications(authentication.getName(), unreadOnly);
    }

    @GetMapping("/notifications/me/unread-count")
    public long unreadCount(Authentication authentication) {
        return notificationService.unreadCount(authentication.getName());
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public NotificationResponse markRead(Authentication authentication, @PathVariable Long notificationId) {
        return notificationService.markRead(authentication.getName(), notificationId);
    }

    @PostMapping("/notifications/me/read-all")
    public long markAllRead(Authentication authentication) {
        return notificationService.markAllRead(authentication.getName());
    }
}
