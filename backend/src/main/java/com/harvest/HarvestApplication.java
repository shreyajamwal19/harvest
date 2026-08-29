package com.harvest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Enables @Scheduled - currently used by the in-memory rate limiters (LoginAttemptService,
// ChefChatRateLimiter, ShowcaseRateLimiter) to periodically evict stale entries. Without this,
// every distinct email/userId/IP that ever hits a rate-limited endpoint accumulates a permanent
// entry in that limiter's map for the lifetime of the process - a slow, real memory leak, and
// one an attacker could accelerate on purpose against ShowcaseRateLimiter by spraying distinct
// spoofed X-Forwarded-For values.
@EnableScheduling
@SpringBootApplication
public class HarvestApplication {
    public static void main(String[] args) {
        SpringApplication.run(HarvestApplication.class, args);
    }
}
