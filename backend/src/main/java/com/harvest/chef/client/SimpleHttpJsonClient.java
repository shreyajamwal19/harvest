package com.harvest.chef.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;

/**
 * Minimal GET-JSON client shared by external knowledge providers (recipe
 * APIs, nutrition APIs, ...). Deliberately tolerant of failure: providers
 * are optional inputs to the Chef Brain, so a network hiccup here should
 * never take down the whole reasoning pipeline - callers get an empty
 * Optional and log the failure themselves.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimpleHttpJsonClient {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Optional<JsonNode> getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.warn("External provider call to {} returned status {}", url, response.statusCode());
                return Optional.empty();
            }

            return Optional.of(objectMapper.readTree(response.body()));
        } catch (IOException e) {
            log.warn("External provider call to {} failed: {}", url, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("External provider call to {} was interrupted", url);
            return Optional.empty();
        }
    }
}
