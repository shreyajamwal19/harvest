package com.harvest.chef.llm;

/**
 * What a single {@link LLMProvider} call actually produced, before {@link LLMProviderManager}
 * attaches which provider handled it and the end-to-end latency (see {@link LLMResult}).
 * Token counts default to -1 when a provider's response doesn't include usage data (should not
 * happen for Gemini/Groq/OpenAI, but a provider is never allowed to fail the whole call just
 * because usage accounting was missing).
 */
public record ProviderCompletion(String text, int inputTokens, int outputTokens) {
}
