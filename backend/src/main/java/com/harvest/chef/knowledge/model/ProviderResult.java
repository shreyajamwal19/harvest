package com.harvest.chef.knowledge.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Unified result envelope for any provider call. The Knowledge Provider
 * Manager and everything downstream of it works with this shape - never
 * with a provider's raw response format.
 */
@Getter
@Builder
public class ProviderResult<T> {
    private T data;
    private boolean success;
    private String providerName;
    /** How confident this specific result is, 0.0-1.0. */
    private double confidence;
    /** How complete this result is relative to what was asked for, 0.0-1.0. */
    private double completeness;
    private long latencyMs;
    /** Provider's baseline track-record reliability, 0.0-1.0 - independent of this single call. */
    private double reliability;
    private String errorMessage;
    private Instant retrievedAt;

    public static <T> ProviderResult<T> failure(String providerName, String errorMessage, long latencyMs) {
        return ProviderResult.<T>builder()
                .data(null)
                .success(false)
                .providerName(providerName)
                .confidence(0.0)
                .completeness(0.0)
                .latencyMs(latencyMs)
                .reliability(0.0)
                .errorMessage(errorMessage)
                .retrievedAt(Instant.now())
                .build();
    }
}
