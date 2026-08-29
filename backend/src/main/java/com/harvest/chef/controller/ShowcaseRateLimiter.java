package com.harvest.chef.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * /api/public/showcase is deliberately unauthenticated (it backs the logged-out homepage), which
 * also means there's no userId to key a limit on and, before this, nothing at all stopping an
 * unlimited number of requests from hammering it - each one makes a real outbound call to
 * TheMealDB. Keyed by client IP instead; same fixed-window, in-memory approach and same stated
 * limitation as {@code ChefChatRateLimiter}/{@code LoginAttemptService} (per-instance, not shared
 * across multiple backend instances). IP is spoofable/shared (NAT, corporate proxies), so this is
 * abuse-dampening, not a strong guarantee - appropriate for a free, keyless, low-value external
 * call, not treated as identity the way userId is for the authenticated limiter.
 */
@Component
@Slf4j
public class ShowcaseRateLimiter {

    private final int maxRequestsPerWindow;
    private final Duration window;

    public ShowcaseRateLimiter(
            @Value("${harvest.showcase.rate-limit.max-requests:30}") int maxRequestsPerWindow,
            @Value("${harvest.showcase.rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    private record WindowState(int count, Instant windowStart) {
    }

    private final Map<String, WindowState> requestsByIp = new ConcurrentHashMap<>();

    public boolean isRateLimited(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return false; // no identity to rate-limit against - fail open rather than block everyone
        }
        Instant now = Instant.now();
        WindowState[] result = new WindowState[1];

        requestsByIp.compute(clientIp, (ip, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(window))) {
                result[0] = new WindowState(1, now);
                return result[0];
            }
            WindowState updated = new WindowState(existing.count() + 1, existing.windowStart());
            result[0] = updated;
            return updated;
        });

        boolean limited = result[0].count() > maxRequestsPerWindow;
        if (limited) {
            log.warn("[showcase] rate limit exceeded for ip={} ({} requests in current window)",
                    clientIp, result[0].count());
        }
        return limited;
    }

    /** Same reasoning as LoginAttemptService.evictExpiredEntries() - and more urgent here, since
     *  this key is a spoofable IP an attacker could deliberately vary to grow this map rather
     *  than just accumulate naturally over time. */
    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    void evictExpiredEntries() {
        Instant now = Instant.now();
        requestsByIp.entrySet().removeIf(entry -> now.isAfter(entry.getValue().windowStart().plus(window)));
    }
}
