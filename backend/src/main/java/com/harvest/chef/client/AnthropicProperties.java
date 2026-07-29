package com.harvest.chef.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "anthropic")
@Getter
@Setter
public class AnthropicProperties {

    /** Set via ANTHROPIC_API_KEY. Never checked into source control. */
    private String apiKey;

    private String model = "claude-sonnet-5";

    private int maxTokens = 1024;
}
