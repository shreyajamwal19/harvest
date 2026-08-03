package com.harvest.chef.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Shared implementation for any provider that speaks the OpenAI chat-completions wire format
 * (Groq and OpenAI both do; a future OpenAI-compatible provider - Mistral, Together, etc. -
 * needs only a new subclass supplying {@link #apiUrl()}, {@link #apiKey()}, {@link #model()},
 * and {@link #name()}). Centralizing this means adding such a provider never touches HTTP,
 * retry, or failover logic.
 */
@Slf4j
public abstract class OpenAiCompatibleLLMProvider extends AbstractLLMProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    protected OpenAiCompatibleLLMProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Full chat-completions endpoint URL for this provider. */
    protected abstract String apiUrl();

    protected abstract String apiKey();

    protected abstract String model();

    @Override
    public boolean isAvailable() {
        String key = apiKey();
        return key != null && !key.isBlank();
    }

    @Override
    protected String doComplete(String systemPrompt, String userPrompt, int maxTokens) {
        long start = System.currentTimeMillis();
        try {
            String requestBody = buildRequestBody(systemPrompt, userPrompt, maxTokens);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                LLMProviderException.ErrorType type =
                        LLMHttpErrorClassifier.classify(response.statusCode(), response.body());
                log.warn("[llm:{}] call failed: status={} type={} latencyMs={}",
                        name(), response.statusCode(), type, latencyMs);
                throw new LLMProviderException(name(), type,
                        name() + " returned status " + response.statusCode());
            }

            String text = extractMessageContent(response.body());
            log.info("[llm:{}] call ok: latencyMs={}", name(), latencyMs);
            return text;
        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("[llm:{}] network failure after {}ms: {}", name(), latencyMs, e.getMessage());
            throw new LLMProviderException(name(), LLMProviderException.ErrorType.UNAVAILABLE,
                    name() + " is unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMProviderException(name(), LLMProviderException.ErrorType.TIMEOUT,
                    name() + " call was interrupted", e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt, int maxTokens) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model());
        root.put("max_tokens", maxTokens);

        ArrayNode messages = root.putArray("messages");
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        return objectMapper.writeValueAsString(root);
    }

    private String extractMessageContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                throw new LLMProviderException(name(), LLMProviderException.ErrorType.UNKNOWN,
                        name() + " returned an empty completion");
            }
            return text;
        } catch (LLMProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMProviderException(name(), LLMProviderException.ErrorType.UNKNOWN,
                    name() + " returned an unparseable response", e);
        }
    }
}
