package com.example.learning_backend.user.enums;

/**
 * Roles a caller may pick for themselves, at registration or when switching later. {@code ADMIN} is
 * deliberately absent: it gates system-wide reports and user administration, so it can only be
 * granted by the seeded bootstrap account or by hand in the database — never through a public API.
 */
public enum AssignableRole {
    STUDENT,
    INSTRUCTOR
}
