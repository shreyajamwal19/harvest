package com.harvest.chef.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /api/chef/chat had no rate limiting at all, despite being by far the most expensive endpoint
 * in the app - a single turn can trigger several real LLM calls (Goal Reasoning, Retrieval
 * Planning, Recipe Evaluation, and the AI Chef Reasoning Layer's explanation), on top of
 * external recipe/nutrition API calls. A compromised account, a buggy frontend retry loop, or
 * someone deliberately hammering the endpoint could run up real API costs with nothing to stop
 * it short of the provider's own account-level limits.
 *
 * Fixed-window limiter per authenticated userId (this endpoint always requires auth, so there's
 * no anonymous/IP case to handle here, unlike login). Same known limitation as
 * {@code LoginAttemptService}: in-memory and per-instance, not shared across multiple backend
 * instances behind a load balancer - still real protection on a single instance, and adds no new
 * dependency.
 */
@Component
@Slf4j
public class ChefChatRateLimiter {

    private final int maxRequestsPerWindow;
    private final Duration window;

    public ChefChatRateLimiter(
            @Value("${harvest.chef-chat.rate-limit.max-requests:20}") int maxRequestsPerWindow,
            @Value("${harvest.chef-chat.rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    private record WindowState(int count, Instant windowStart) {
    }

    private final Map<Long, WindowState> requestsByUserId = new ConcurrentHashMap<>();

    /** True if this user has exceeded the request limit for the current window. */
    public boolean isRateLimited(Long userId) {
        Instant now = Instant.now();
        WindowState[] result = new WindowState[1];

        requestsByUserId.compute(userId, (id, existing) -> {
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
            log.warn("[chef-chat] rate limit exceeded for userId={} ({} requests in current window)",
                    userId, result[0].count());
        }
        return limited;
    }
}
