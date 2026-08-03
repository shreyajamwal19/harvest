package com.harvest.chef.llm;

/** Successful completion plus which provider actually handled it, for logging/observability. */
public record LLMResult(String text, String providerName, long latencyMs) {
}
