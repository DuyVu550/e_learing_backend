package com.example.learning_backend.config;

import com.example.learning_backend.user.entity.Role;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.enums.UserStatus;
import com.example.learning_backend.user.repository.RoleRepository;
import com.example.learning_backend.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RoleDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleDataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminFullName;

    public RoleDataInitializer(
        RoleRepository roleRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.admin.email}") String adminEmail,
        @Value("${app.admin.password}") String adminPassword,
        @Value("${app.admin.full-name}") String adminFullName
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminFullName = adminFullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRole("STUDENT", "Student");
        ensureRole("INSTRUCTOR", "Instructor");
        ensureRole("ADMIN", "Admin");
        ensureAdminUser();
    }

    private void ensureRole(String code, String name) {
        if (roleRepository.findByCode(code).isPresent()) {
            return;
        }

        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        roleRepository.save(role);
    }

    /**
     * Bootstraps the one ADMIN account. {@code ADMIN} is not in {@link
     * com.example.learning_backend.user.enums.AssignableRole}, so no API call can mint it — without
     * this seed the admin-only endpoints would be unreachable. Only ever creates the account when it
     * is missing: an existing admin's password is left alone so a deployment restart cannot reset it.
     */
    private void ensureAdminUser() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("Admin seed skipped: app.admin.email/password are not set");
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }

        Role adminRole = roleRepository.findByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("Role not found: ADMIN"));

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFullName(adminFullName);
        admin.setStatus(UserStatus.ACTIVE);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
        log.info("Seeded admin account {}", adminEmail);
    }
}
