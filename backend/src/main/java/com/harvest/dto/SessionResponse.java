package com.harvest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Returned by GET /api/user/me - lets the frontend re-hydrate auth state (e.g. on
 * page refresh) and know exactly when the current session will expire.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    private UserDto user;
    private Instant expiresAt;
}
