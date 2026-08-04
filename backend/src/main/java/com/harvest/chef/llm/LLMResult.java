package com.harvest.chef.llm;

/**
 * Successful completion plus which provider actually handled it and its token usage, for
 * observability. {@code inputTokens}/{@code outputTokens} are -1 when the provider's response
 * didn't include usage data.
 */
public record LLMResult(String text, String providerName, long latencyMs, int inputTokens, int outputTokens) {
}
