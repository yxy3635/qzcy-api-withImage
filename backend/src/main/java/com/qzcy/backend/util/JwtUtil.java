package com.qzcy.backend.util;

import com.qzcy.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.JwtException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    @PostConstruct
    public void checkConfiguration() {
        key();
        if (expirationMs <= 0) throw new IllegalStateException("JWT expiration must be positive");
    }
    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .claim("authVersion", user.getAuthVersion() == null ? 0L : user.getAuthVersion())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Claims payload = claims(token);
            return payload.get("userId", Long.class) != null && payload.getSubject() != null
                    && payload.get("authVersion", Long.class) != null && payload.getExpiration() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return claims(token).get("userId", Long.class);
    }

    public String getUsername(String token) {
        return claims(token).getSubject();
    }

    public String getRole(String token) {
        return claims(token).get("role", String.class);
    }

    /** Missing versions are deliberately rejected: upgrading logs out all legacy sessions. */
    public boolean matchesUser(String token, User user) {
        try {
            Claims payload = claims(token);
            Long version = payload.get("authVersion", Long.class);
            return user != null && user.getId().equals(payload.get("userId", Long.class))
                    && user.getUsername().equals(payload.getSubject()) && version != null
                    && version.equals(user.getAuthVersion() == null ? 0L : user.getAuthVersion());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey key() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.toLowerCase(java.util.Locale.ROOT).matches(".*(development|change-me|changeme|replace-me|your-secret|example).*")
                || secret.chars().distinct().count() < 12) {
            throw new IllegalStateException("JWT_SECRET must be an independent random secret of at least 32 bytes; generate with openssl rand -hex 32");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
