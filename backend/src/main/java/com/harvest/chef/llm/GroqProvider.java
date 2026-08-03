package com.harvest.chef.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Fallback #1: tried only if Gemini is unavailable or fails. */
@Component
public class GroqProvider extends OpenAiCompatibleLLMProvider {

    private final GroqProperties properties;

    // Explicit constructor because we need both the injected ObjectMapper (for the parent) and
    // GroqProperties (for this class) - Lombok's @RequiredArgsConstructor can't populate a
    // superclass constructor, so this is written out by hand instead.
    public GroqProvider(ObjectMapper objectMapper, GroqProperties properties) {
        super(objectMapper);
        this.properties = properties;
    }

    @Override
    public String name() {
        return "groq";
    }

    @Override
    protected String apiUrl() {
        return properties.getBaseUrl();
    }

    @Override
    protected String apiKey() {
        return properties.getApiKey();
    }

    @Override
    protected String model() {
        return properties.getModel();
    }
}
