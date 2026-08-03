package com.example.learning_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.learning_backend.auth.dto.AuthResponse;
import com.example.learning_backend.auth.dto.LoginRequest;
import com.example.learning_backend.auth.dto.RegisterRequest;
import com.example.learning_backend.auth.dto.RoleChangeRequest;
import com.example.learning_backend.auth.service.AuthService;
import com.example.learning_backend.user.entity.Role;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.enums.AssignableRole;
import com.example.learning_backend.user.repository.RoleRepository;
import com.example.learning_backend.user.repository.UserRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthRoleServiceTests {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void registeringWithoutARoleStillMakesAStudent() {
        AuthResponse response = authService.register(
            new RegisterRequest("plain@example.com", PASSWORD, "Plain", null)
        );

        assertThat(response.user().roles()).containsExactly("STUDENT");
    }

    @Test
    void registeringAsAnInstructorGrantsTheInstructorRole() {
        AuthResponse response = authService.register(
            new RegisterRequest("teacher@example.com", PASSWORD, "Teacher", AssignableRole.INSTRUCTOR)
        );

        assertThat(response.user().roles()).containsExactly("INSTRUCTOR");
        assertThat(rolesOf("teacher@example.com")).containsExactly("INSTRUCTOR");
    }

    @Test
    void anInstructorCanBeLoggedInAndKeepsTheRole() {
        authService.register(new RegisterRequest("t2@example.com", PASSWORD, "T2", AssignableRole.INSTRUCTOR));

        AuthResponse login = authService.login(new LoginRequest("t2@example.com", PASSWORD));

        assertThat(login.user().roles()).containsExactly("INSTRUCTOR");
    }

    @Test
    void aStudentCanSwitchToInstructorAndBack() {
        authService.register(new RegisterRequest("switch@example.com", PASSWORD, "Switcher", null));

        AuthResponse promoted = authService.changeRole(
            "switch@example.com",
            new RoleChangeRequest(AssignableRole.INSTRUCTOR)
        );
        assertThat(promoted.user().roles()).containsExactly("INSTRUCTOR");
        assertThat(rolesOf("switch@example.com")).containsExactly("INSTRUCTOR");

        AuthResponse demoted = authService.changeRole(
            "switch@example.com",
            new RoleChangeRequest(AssignableRole.STUDENT)
        );
        assertThat(demoted.user().roles()).containsExactly("STUDENT");
        assertThat(rolesOf("switch@example.com")).containsExactly("STUDENT");
    }

    @Test
    void changingRoleIssuesAFreshTokenCarryingTheNewRole() {
        AuthResponse before = authService.register(
            new RegisterRequest("fresh@example.com", PASSWORD, "Fresh", null)
        );

        AuthResponse after = authService.changeRole(
            "fresh@example.com",
            new RoleChangeRequest(AssignableRole.INSTRUCTOR)
        );

        assertThat(after.accessToken()).isNotEqualTo(before.accessToken());
        assertThat(after.user().roles()).containsExactly("INSTRUCTOR");
    }

    @Test
    void switchingToTheSameRoleIsHarmless() {
        authService.register(new RegisterRequest("same@example.com", PASSWORD, "Same", null));

        AuthResponse response = authService.changeRole(
            "same@example.com",
            new RoleChangeRequest(AssignableRole.STUDENT)
        );

        assertThat(response.user().roles()).containsExactly("STUDENT");
    }

    /** ADMIN is outside {@link AssignableRole}, so a self-service switch must not strip it. */
    @Test
    void anAdminKeepsAdminAfterPickingAStudentRole() {
        User admin = userRepository.findByEmail("admin@test.local").orElseThrow();
        Role adminRole = roleRepository.findByCode("ADMIN").orElseThrow();
        assertThat(admin.getRoles()).contains(adminRole);

        AuthResponse response = authService.changeRole(
            "admin@test.local",
            new RoleChangeRequest(AssignableRole.STUDENT)
        );

        assertThat(response.user().roles()).contains("ADMIN", "STUDENT");
    }

    @Test
    void theAdminAccountIsSeededWithAUsablePassword() {
        AuthResponse login = authService.login(new LoginRequest("admin@test.local", "Admin@12345"));

        assertThat(login.user().roles()).containsExactly("ADMIN");
    }

    @Test
    void registeringAnExistingEmailIsStillRejected() {
        authService.register(new RegisterRequest("dup@example.com", PASSWORD, "Dup", AssignableRole.INSTRUCTOR));

        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("dup@example.com", PASSWORD, "Dup Again", AssignableRole.STUDENT)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email already exists");
    }

    private Set<String> rolesOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getRoles()
            .stream().map(Role::getCode).collect(Collectors.toSet());
    }
}
