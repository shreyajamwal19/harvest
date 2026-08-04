package com.harvest.chef.llm;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared retry wrapper. A single automatic retry is attempted, and only for failures classified
 * as transient ({@link LLMProviderException#isRetryable()}) - auth/quota/rate-limit failures are
 * never retried, since retrying them can't succeed and just adds latency before
 * {@link LLMProviderManager} fails over to the next provider. Subclasses implement the actual
 * HTTP call in {@link #doComplete}.
 */
@Slf4j
public abstract class AbstractLLMProvider implements LLMProvider {

    private static final int MAX_ATTEMPTS = 2;

    @Override
    public final ProviderCompletion complete(String systemPrompt, String userPrompt, int maxTokens) {
        LLMProviderException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return doComplete(systemPrompt, userPrompt, maxTokens);
            } catch (LLMProviderException e) {
                lastFailure = e;
                boolean willRetry = e.isRetryable() && attempt < MAX_ATTEMPTS;
                if (willRetry) {
                    log.debug("[llm:{}] attempt {}/{} failed ({}), retrying once: {}",
                            name(), attempt, MAX_ATTEMPTS, e.getErrorType(), e.getMessage());
                } else {
                    throw e;
                }
            }
        }

        // Unreachable in practice (the loop always returns or throws), but keeps the compiler
        // happy and fails loudly rather than silently if that ever changes.
        throw lastFailure != null
                ? lastFailure
                : new LLMProviderException(name(), LLMProviderException.ErrorType.UNKNOWN, "No attempts were made");
    }

    /** The actual provider call for a single attempt. Never retried internally - see {@link #complete}. */
    protected abstract ProviderCompletion doComplete(String systemPrompt, String userPrompt, int maxTokens);
}
