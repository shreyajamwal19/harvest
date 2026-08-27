package com.harvest.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * There was previously nothing standing between the /api/auth/login endpoint and an unlimited
 * number of password guesses - no rate limit, no lockout, nothing. Anyone could script
 * credential-stuffing or brute-force attempts against any known email at whatever rate the
 * server would accept.
 *
 * Simple, dependency-free per-email lockout: after MAX_FAILED_ATTEMPTS failures within
 * FAILURE_WINDOW, further attempts for that email are rejected for LOCKOUT_DURATION regardless
 * of whether the password is actually correct. Keyed by normalized email, not IP - a determined
 * attacker rotating IPs is still rate-limited the same as one that isn't, and legitimate users
 * behind a shared/proxied IP are never penalized for someone else's failed attempts.
 *
 * Known limitation, stated plainly rather than implied: this is in-memory and per-instance. It
 * does nothing across multiple backend instances behind a load balancer - each instance tracks
 * its own counts. That's still strictly better than no protection at all on a single instance,
 * and doesn't require adding Redis or any other new dependency (unreachable from this sandbox to
 * verify anyway); a shared store is the natural upgrade path if Harvest ever runs multi-instance.
 */
@Component
@Slf4j
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private record AttemptRecord(AtomicInteger failureCount, Instant windowStart, Instant lockedUntil) {
    }

    private final Map<String, AttemptRecord> attemptsByEmail = new ConcurrentHashMap<>();

    /** True if this email is currently locked out from further login attempts. */
    public boolean isLocked(String normalizedEmail) {
        AttemptRecord record = attemptsByEmail.get(normalizedEmail);
        if (record == null || record.lockedUntil() == null) {
            return false;
        }
        if (Instant.now().isAfter(record.lockedUntil())) {
            // Lockout has expired - clean up so the next failure starts a fresh window
            // instead of comparing against a stale one.
            attemptsByEmail.remove(normalizedEmail, record);
            return false;
        }
        return true;
    }

    /** Records a failed login attempt, locking the account out once the threshold is crossed. */
    public void recordFailure(String normalizedEmail) {
        Instant now = Instant.now();
        attemptsByEmail.compute(normalizedEmail, (email, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(FAILURE_WINDOW))) {
                // No record yet, or the previous failure window has fully expired - start over.
                return new AttemptRecord(new AtomicInteger(1), now, null);
            }
            int failures = existing.failureCount().incrementAndGet();
            if (failures >= MAX_FAILED_ATTEMPTS) {
                log.warn("[auth] locking out login attempts for an email after {} failures within {}",
                        failures, FAILURE_WINDOW);
                return new AttemptRecord(existing.failureCount(), existing.windowStart(), now.plus(LOCKOUT_DURATION));
            }
            return existing;
        });
    }

    /** Clears any tracked failures on a successful login, so a locked-out account isn't stuck longer than necessary. */
    public void recordSuccess(String normalizedEmail) {
        attemptsByEmail.remove(normalizedEmail);
    }
}
