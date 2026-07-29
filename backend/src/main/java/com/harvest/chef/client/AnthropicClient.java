package com.harvest.chef.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.harvest.chef.exception.ChefReasoningException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Thin wrapper around the Anthropic /v1/messages endpoint. Every reasoning
 * stage goes through this single class - it is the only place that knows
 * about request/response wire format, so upgrading models or API shape
 * later touches one file, not five.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnthropicClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @PostConstruct
    void validateConfig() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("ANTHROPIC_API_KEY is not set - Chef Brain reasoning calls will fail until it is configured.");
        }
    }

    public String send(String systemPrompt, String userPrompt, int maxTokens) {
        try {
            String requestBody = buildRequestBody(systemPrompt, userPrompt, maxTokens);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", API_VERSION)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("Anthropic API error: status={} body={}", response.statusCode(), response.body());
                throw new ChefReasoningException(
                        "The AI reasoning service returned an error (status " + response.statusCode() + ")");
            }

            return extractText(response.body());
        } catch (IOException e) {
            log.error("Failed to call Anthropic API", e);
            throw new ChefReasoningException("Unable to reach the AI reasoning service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChefReasoningException("The AI reasoning call was interrupted", e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt, int maxTokens) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("max_tokens", maxTokens);
        root.put("system", systemPrompt);

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        return objectMapper.writeValueAsString(root);
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode responseJson = objectMapper.readTree(responseBody);
        JsonNode contentArray = responseJson.path("content");

        StringBuilder text = new StringBuilder();
        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
        }

        if (text.isEmpty()) {
            throw new ChefReasoningException("The AI reasoning service returned an empty response");
        }

        return text.toString();
    }
}
