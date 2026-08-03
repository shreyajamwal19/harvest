package com.harvest.chef.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Optional. If {@code apiKey} is unset, GroqProvider reports unavailable and is skipped. */
@Configuration
@ConfigurationProperties(prefix = "groq")
@Getter
@Setter
public class GroqProperties {

    /** Set via GROQ_API_KEY. Never checked into source control. */
    private String apiKey;

    private String model = "llama-3.3-70b-versatile";

    private String baseUrl = "https://api.groq.com/openai/v1/chat/completions";

    private int maxTokens = 500;
}
