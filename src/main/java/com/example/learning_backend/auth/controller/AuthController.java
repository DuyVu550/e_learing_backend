package com.example.learning_backend.auth.controller;

import com.example.learning_backend.auth.dto.AuthResponse;
import com.example.learning_backend.auth.dto.ChangePasswordRequest;
import com.example.learning_backend.auth.dto.ForgotPasswordRequest;
import com.example.learning_backend.auth.dto.ForgotPasswordResponse;
import com.example.learning_backend.auth.dto.LoginRequest;
import com.example.learning_backend.auth.dto.LogoutRequest;
import com.example.learning_backend.auth.dto.RefreshTokenRequest;
import com.example.learning_backend.auth.dto.RegisterRequest;
import com.example.learning_backend.auth.dto.ResetPasswordRequest;
import com.example.learning_backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    @PostMapping("/change-password")
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
    }
}
