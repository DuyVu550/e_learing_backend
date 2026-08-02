package com.example.learning_backend.course.service;

import com.example.learning_backend.course.entity.Course;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Central rule for "may this caller manage this course?": admins always may, otherwise only the
 * owning instructor. Shared so the check cannot drift between the services that enforce it.
 */
@Component
public class CourseAccessPolicy {

    public boolean canManage(Course course, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (admin) {
            return true;
        }
        return course.getInstructor() != null
            && course.getInstructor().getEmail().equals(authentication.getName());
    }

    public void ensureCanManage(Course course, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        if (!canManage(course, authentication)) {
            throw new IllegalArgumentException("You cannot manage this course");
        }
    }
}
