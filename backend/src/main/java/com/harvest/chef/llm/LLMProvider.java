package com.harvest.chef.llm;

/**
 * The common contract every LLM backend implements. {@code LLMProviderManager} only ever
 * talks to providers through this shape - it never knows how a provider actually calls its
 * API. Adding a new provider (e.g. a future one) means adding one new class that implements
 * this interface (or, for anything OpenAI-compatible, extends {@link OpenAiCompatibleLLMProvider})
 * and registering it in {@link LLMProviderManager}'s constructor - no other business logic
 * changes.
 *
 * Implementations are used EXCLUSIVELY by the AI Chef Reasoning Layer
 * ({@code com.harvest.chef.reasoning.ChefReasoningService}) to talk about recipes the
 * deterministic pipeline has already retrieved and ranked. Nothing in this package searches,
 * ranks, or retrieves recipes.
 */
public interface LLMProvider {

    /** Short, stable identifier used in logs - e.g. "gemini", "groq", "openai". */
    String name();

    /** Cheap, fast check (e.g. is an API key configured) - never makes a network call. */
    boolean isAvailable();

    /**
     * Calls the provider and returns its completion, including token usage where the provider
     * reports it.
     *
     * @throws LLMProviderException on any failure (auth, quota, rate limit, timeout, or
     *                              anything else) - never returns null or partial output.
     */
    ProviderCompletion complete(String systemPrompt, String userPrompt, int maxTokens);
}
