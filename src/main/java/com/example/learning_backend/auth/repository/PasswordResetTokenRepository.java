package com.example.learning_backend.auth.repository;

import com.example.learning_backend.auth.entity.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
