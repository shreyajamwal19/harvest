package com.harvest.chef.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Optional. If {@code apiKey} is unset, OpenAIProvider reports unavailable and is skipped. */
@Configuration
@ConfigurationProperties(prefix = "openai")
@Getter
@Setter
public class OpenAiProperties {

    /** Set via OPENAI_API_KEY. Never checked into source control. */
    private String apiKey;

    private String model = "gpt-4o-mini";

    private String baseUrl = "https://api.openai.com/v1/chat/completions";

    private int maxTokens = 500;
}
