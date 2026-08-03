package com.harvest.chef.llm;

/**
 * Thrown by any {@link LLMProvider} on failure. Classified into an {@link ErrorType} so
 * {@link LLMProviderManager} can log a structured reason for failover, and so
 * {@link AbstractLLMProvider} knows whether a single automatic retry within the same provider
 * is worth attempting before giving up and letting the manager fail over to the next one.
 */
public class LLMProviderException extends RuntimeException {

    public enum ErrorType {
        /** Provider's usage quota/credits are exhausted - retrying won't help. */
        QUOTA_EXCEEDED,
        /** Too many requests right now - retrying immediately won't help either. */
        RATE_LIMITED,
        /** The call didn't complete in time - worth one quick retry. */
        TIMEOUT,
        /** Bad/missing/expired API key - retrying won't help. */
        AUTH_FAILURE,
        /** Provider unreachable (network/DNS/5xx) - worth one quick retry. */
        UNAVAILABLE,
        /** Anything else (unexpected status code, malformed response, ...). */
        UNKNOWN
    }

    private final String providerName;
    private final ErrorType errorType;

    public LLMProviderException(String providerName, ErrorType errorType, String message) {
        this(providerName, errorType, message, null);
    }

    public LLMProviderException(String providerName, ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
        this.errorType = errorType;
    }

    public String getProviderName() {
        return providerName;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    /** Whether a single automatic retry within the same provider is worth attempting. */
    public boolean isRetryable() {
        return errorType == ErrorType.TIMEOUT || errorType == ErrorType.UNAVAILABLE;
    }
}
