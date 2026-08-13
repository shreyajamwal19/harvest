package com.harvest.chef.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Primary provider: tried first on every AI Chef Reasoning Layer call. Google's Generative
 * Language API uses a different request/response shape than the OpenAI-compatible providers
 * (see {@link OpenAiCompatibleLLMProvider}), so this implements {@link AbstractLLMProvider}
 * directly rather than sharing that base.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiProvider extends AbstractLLMProvider {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    @Override
    protected ProviderCompletion doComplete(String systemPrompt, String userPrompt, int maxTokens) {
        long start = System.currentTimeMillis();
        try {
            // The API key was previously passed as a "?key=" query parameter. Query strings are
            // routinely captured verbatim by proxies, load balancers, and HTTP client debug/wire
            // logging, so any of those logging the request URL would leak the credential. Gemini
            // supports the same auth via the "x-goog-api-key" header instead - functionally
            // identical, but never appears in a URL that could end up in a log line.
            String url = API_BASE + properties.getModel() + ":generateContent";
            String requestBody = buildRequestBody(systemPrompt, userPrompt, maxTokens);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", properties.getApiKey())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                LLMProviderException.ErrorType type =
                        LLMHttpErrorClassifier.classify(response.statusCode(), response.body());
                log.warn("[llm:gemini] call failed: status={} type={} latencyMs={}",
                        response.statusCode(), type, latencyMs);
                throw new LLMProviderException(name(), type, "gemini returned status " + response.statusCode());
            }

            ProviderCompletion completion = extractCompletion(response.body());
            log.info("[llm:gemini] call ok: latencyMs={} inputTokens={} outputTokens={}",
                    latencyMs, completion.inputTokens(), completion.outputTokens());
            return completion;
        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("[llm:gemini] network failure after {}ms: {}", latencyMs, e.getMessage());
            throw new LLMProviderException(name(), LLMProviderException.ErrorType.UNAVAILABLE,
                    "gemini is unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMProviderException(name(), LLMProviderException.ErrorType.TIMEOUT,
                    "gemini call was interrupted", e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt, int maxTokens) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode systemInstruction = root.putObject("system_instruction");
        ArrayNode systemParts = systemInstruction.putArray("parts");
        systemParts.addObject().put("text", systemPrompt);

        ArrayNode contents = root.putArray("contents");
        ObjectNode userContent = contents.addObject();
        ArrayNode userParts = userContent.putArray("parts");
        userParts.addObject().put("text", userPrompt);

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("maxOutputTokens", maxTokens);

        return objectMapper.writeValueAsString(root);
    }

    private ProviderCompletion extractCompletion(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new LLMProviderException(name(), LLMProviderException.ErrorType.UNKNOWN,
                        "gemini returned an empty completion");
            }
            JsonNode usage = root.path("usageMetadata");
            int inputTokens = usage.path("promptTokenCount").asInt(-1);
            int outputTokens = usage.path("candidatesTokenCount").asInt(-1);
            return new ProviderCompletion(text, inputTokens, outputTokens);
        } catch (LLMProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMProviderException(name(), LLMProviderException.ErrorType.UNKNOWN,
                    "gemini returned an unparseable response", e);
        }
    }
}
