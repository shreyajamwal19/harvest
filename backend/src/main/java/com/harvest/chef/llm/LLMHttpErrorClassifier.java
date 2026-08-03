package com.harvest.chef.llm;

/** Shared by every provider's HTTP call site so this mapping exists in exactly one place. */
final class LLMHttpErrorClassifier {

    private LLMHttpErrorClassifier() {
    }

    /** Best-effort classification from HTTP status (+ body keywords for the 429 quota/rate-limit split). */
    static LLMProviderException.ErrorType classify(int status, String body) {
        return switch (status) {
            case 401, 403 -> LLMProviderException.ErrorType.AUTH_FAILURE;
            case 429 -> body != null && body.toLowerCase().contains("quota")
                    ? LLMProviderException.ErrorType.QUOTA_EXCEEDED
                    : LLMProviderException.ErrorType.RATE_LIMITED;
            case 408, 504 -> LLMProviderException.ErrorType.TIMEOUT;
            case 500, 502, 503 -> LLMProviderException.ErrorType.UNAVAILABLE;
            default -> LLMProviderException.ErrorType.UNKNOWN;
        };
    }
}
