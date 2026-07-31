package com.example.learning_backend.auth.service;

import com.example.learning_backend.auth.dto.AuthResponse;
import com.example.learning_backend.auth.dto.ChangePasswordRequest;
import com.example.learning_backend.auth.dto.ForgotPasswordRequest;
import com.example.learning_backend.auth.dto.ForgotPasswordResponse;
import com.example.learning_backend.auth.dto.LoginRequest;
import com.example.learning_backend.auth.dto.LogoutRequest;
import com.example.learning_backend.auth.dto.RefreshTokenRequest;
import com.example.learning_backend.auth.dto.RegisterRequest;
import com.example.learning_backend.auth.dto.ResetPasswordRequest;
import com.example.learning_backend.auth.dto.UserResponse;
import com.example.learning_backend.auth.entity.PasswordResetToken;
import com.example.learning_backend.auth.entity.RefreshToken;
import com.example.learning_backend.auth.repository.PasswordResetTokenRepository;
import com.example.learning_backend.auth.repository.RefreshTokenRepository;
import com.example.learning_backend.auth.security.JwtTokenService;
import com.example.learning_backend.user.entity.Role;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.enums.UserStatus;
import com.example.learning_backend.user.repository.RoleRepository;
import com.example.learning_backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private static final String DEFAULT_ROLE = "STUDENT";
    private static final int RESET_TOKEN_BYTES = 32;
    private static final long RESET_TOKEN_TTL_MINUTES = 30;

    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Role studentRole = roleRepository.findByCode(DEFAULT_ROLE)
            .orElseThrow(() -> new IllegalStateException("Default role STUDENT not found"));

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(studentRole);

        return issueTokenPair(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        assertActive(user);
        return issueTokenPair(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtTokenService.isRefreshToken(request.refreshToken())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String tokenHash = hashToken(request.refreshToken());
        RefreshToken savedToken = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (!savedToken.isActive()) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        savedToken.setRevokedAt(LocalDateTime.now());
        User user = savedToken.getUser();
        assertActive(user);
        return issueTokenPair(user);
    }

    public void logout(LogoutRequest request) {
        String tokenHash = hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash)
            .ifPresent(token -> token.setRevokedAt(LocalDateTime.now()));
    }

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email())
            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
            .ifPresent(user -> {
                String resetToken = randomToken();
                PasswordResetToken token = new PasswordResetToken();
                token.setUser(user);
                token.setTokenHash(hashToken(resetToken));
                token.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
                passwordResetTokenRepository.save(token);
                // ponytail: no mail dependency yet; wire resetToken delivery when SMTP config exists.
            });
        return new ForgotPasswordResponse("If the email exists, a password reset link will be sent.");
    }

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashToken(request.resetToken()))
            .orElseThrow(() -> new BadCredentialsException("Invalid reset token"));
        if (!token.isActive()) {
            throw new BadCredentialsException("Reset token expired or used");
        }

        User user = token.getUser();
        assertActive(user);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        token.setUsedAt(LocalDateTime.now());
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("Invalid user"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private AuthResponse issueTokenPair(User user) {
        Set<String> roles = roleCodes(user);
        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtTokenService.generateRefreshToken(user.getId(), user.getEmail(), roles);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hashToken(refreshToken));
        token.setExpiresAt(LocalDateTime.now().plus(jwtTokenService.getRefreshTtl()));
        refreshTokenRepository.save(token);

        return new AuthResponse(
            toUserResponse(user),
            accessToken,
            refreshToken,
            "Bearer",
            jwtTokenService.getAccessTtl().toSeconds()
        );
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getAvatarUrl(),
            user.getStatus().name(),
            roleCodes(user)
        );
    }

    private Set<String> roleCodes(User user) {
        return user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
    }

    private void assertActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User is not active");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
