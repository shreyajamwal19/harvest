package com.harvest.chef.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Fallback #2: tried only if both Gemini and Groq are unavailable or fail. */
@Component
public class OpenAIProvider extends OpenAiCompatibleLLMProvider {

    private final OpenAiProperties properties;

    // Explicit constructor - see GroqProvider for why @RequiredArgsConstructor doesn't fit here.
    public OpenAIProvider(ObjectMapper objectMapper, OpenAiProperties properties) {
        super(objectMapper);
        this.properties = properties;
    }

    @Override
    public String name() {
        return "openai";
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
