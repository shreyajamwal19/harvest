package com.harvest.chef.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The single entry point the AI Chef Reasoning Layer calls for any LLM completion. Owns
 * provider selection and automatic failover - callers never talk to an {@link LLMProvider}
 * directly and never see a provider-specific failure.
 *
 * Fixed priority order: Gemini (primary) -> Groq (fallback #1) -> OpenAI (fallback #2). Each
 * unavailable/failing provider is skipped/failed-over silently from the caller's perspective;
 * only a structured log line records which provider (if any) actually handled the call, and
 * whether that was the primary or a fallback. If every provider is unavailable or fails,
 * {@link #complete} returns {@link Optional#empty()} and the caller falls back to Harvest's
 * existing deterministic explanation logic - the user never sees a provider failure either way.
 */
@Component
@Slf4j
public class LLMProviderManager {

    private final List<LLMProvider> orderedProviders;

    // Explicit constructor parameters (not a Spring-collected List<LLMProvider>) so the
    // Gemini -> Groq -> OpenAI priority order is guaranteed by construction, not by whatever
    // order Spring happens to discover beans in.
    public LLMProviderManager(GeminiProvider gemini, GroqProvider groq, OpenAIProvider openai) {
        this.orderedProviders = List.of(gemini, groq, openai);
    }

    /**
     * @param systemPrompt the mode-specific system prompt (see {@code com.harvest.chef.reasoning.prompt})
     * @param userPrompt   the grounded user turn built by the same prompt builder
     * @param maxTokens    output token budget for this call
     */
    public Optional<LLMResult> complete(String systemPrompt, String userPrompt, int maxTokens) {
        String primaryProviderName = orderedProviders.get(0).name();

        for (LLMProvider provider : orderedProviders) {
            if (!provider.isAvailable()) {
                log.debug("[llm] {} not configured, skipping", provider.name());
                continue;
            }

            boolean isFallback = !provider.name().equals(primaryProviderName);
            long start = System.currentTimeMillis();
            try {
                ProviderCompletion completion = provider.complete(systemPrompt, userPrompt, maxTokens);
                long latencyMs = System.currentTimeMillis() - start;
                log.info("[llm] request handled by provider={} fallback={} latencyMs={} "
                                + "inputTokens={} outputTokens={}",
                        provider.name(), isFallback, latencyMs, completion.inputTokens(), completion.outputTokens());
                return Optional.of(new LLMResult(completion.text(), provider.name(), latencyMs,
                        completion.inputTokens(), completion.outputTokens()));
            } catch (LLMProviderException e) {
                long latencyMs = System.currentTimeMillis() - start;
                log.warn("[llm] provider={} failed (type={}, latencyMs={}): {} - failing over to next provider",
                        provider.name(), e.getErrorType(), latencyMs, e.getMessage());
            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - start;
                log.warn("[llm] provider={} threw an unexpected exception (latencyMs={}): {} - "
                        + "failing over to next provider", provider.name(), latencyMs, e.getMessage());
            }
        }

        log.warn("[llm] all providers unavailable or failed - falling back to Harvest's "
                + "deterministic explanation logic");
        return Optional.empty();
    }
}
