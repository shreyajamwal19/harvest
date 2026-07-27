package com.harvest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Returned on successful signup/login. The JWT itself is NEVER included in this body -
 * it is delivered exclusively via an httpOnly Set-Cookie header so client-side JS
 * (and any XSS payload) can never read it. expiresAt lets the frontend proactively
 * schedule an auto-logout without needing to decode the token.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private UserDto user;
    private String message;
    private Instant expiresAt;
}
