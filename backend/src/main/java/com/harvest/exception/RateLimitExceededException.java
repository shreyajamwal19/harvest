package com.harvest.exception;

/** Thrown when a per-user rate limit (e.g. ChefChatRateLimiter) has been exceeded. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
