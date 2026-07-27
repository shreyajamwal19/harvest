package com.harvest.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 64; // 512 bits, required for HS512

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    private SecretKey signingKey;

    /**
     * Fail fast on startup with a clear error if the configured secret is too weak for
     * HS512, instead of letting every login/signup request blow up with a WeakKeyException.
     */
    @PostConstruct
    void init() {
        int byteLength = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret is too short (" + byteLength + " bytes). HS512 requires at least "
                            + MIN_SECRET_BYTES + " bytes (512 bits). Set a stronger JWT_SECRET env var.");
        }
        try {
            this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        } catch (WeakKeyException e) {
            throw new IllegalStateException("jwt.secret does not meet HS512 key strength requirements", e);
        }
        if (jwtExpirationMs <= 0) {
            throw new IllegalStateException("jwt.expiration-ms must be a positive number of milliseconds");
        }
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateToken(Authentication authentication) {
        return generateToken(authentication.getName());
    }

    public String generateToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public Instant getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException | ExpiredJwtException |
                 UnsupportedJwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }
}
