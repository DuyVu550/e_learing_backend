package com.example.learning_backend.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-ttl}") Duration accessTtl,
            @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String generateAccessToken(Long userId, String email, Set<String> roles) {
        return buildToken(userId, email, roles, "access", accessTtl);
    }

    public String generateRefreshToken(Long userId, String email, Set<String> roles) {
        return buildToken(userId, email, roles, "refresh", refreshTtl);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseClaims(token).get("tokenType", String.class));
    }

    public boolean isAccessToken(String token) {
        return "access".equals(parseClaims(token).get("tokenType", String.class));
    }

    public Long getUserId(String token) {
        Object value = parseClaims(token).get("userId");
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("")
    public Set<String> getRoles(String token) {
        Object roles = parseClaims(token).get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    private String buildToken(Long userId, String email, Set<String> roles, String tokenType, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claims(Map.of(
                        "userId", userId,
                        "roles", roles,
                        "tokenType", tokenType))
                .signWith(secretKey)
                .compact();
    }
}
