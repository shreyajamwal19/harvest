package com.harvest.chef.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Optional. If {@code apiKey} is unset, GeminiProvider reports unavailable and is skipped. */
@Configuration
@ConfigurationProperties(prefix = "gemini")
@Getter
@Setter
public class GeminiProperties {

    /** Set via GEMINI_API_KEY. Never checked into source control. */
    private String apiKey;

    private String model = "gemini-2.0-flash";

    private int maxTokens = 500;
}
