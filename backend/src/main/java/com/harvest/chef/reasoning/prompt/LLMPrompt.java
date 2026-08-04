package com.harvest.chef.reasoning.prompt;

/** A ready-to-send (system, user) prompt pair produced by one of the prompt builders below. */
public record LLMPrompt(String systemPrompt, String userPrompt) {
}
