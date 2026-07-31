package com.example.learning_backend.config;

import com.example.learning_backend.user.entity.Role;
import com.example.learning_backend.user.repository.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RoleDataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;

    public RoleDataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRole("STUDENT", "Student");
        ensureRole("INSTRUCTOR", "Instructor");
        ensureRole("ADMIN", "Admin");
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
}
