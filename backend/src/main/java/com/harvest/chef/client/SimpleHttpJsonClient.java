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
import java.util.regex.Pattern;

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

    // UsdaNutritionProvider (and any future caller) builds its request URL with the API key
    // embedded directly as a query parameter (?api_key=...) - the USDA FoodData Central API only
    // documents that form, not a header alternative, so the URL itself carries the secret. This
    // client used to log that full URL verbatim on every failed call, meaning the key would end
    // up in Harvest's own application logs (and wherever those get shipped/aggregated) every time
    // a nutrition lookup timed out or errored - not a wire-level leak (still HTTPS), but a real
    // one into logging infrastructure. Redacted before logging; the request itself is unchanged.
    private static final Pattern SENSITIVE_QUERY_PARAM =
            Pattern.compile("(?i)([?&](?:api_key|apikey|key|token)=)[^&]*");

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
                log.warn("External provider call to {} returned status {}", redact(url), response.statusCode());
                return Optional.empty();
            }

            return Optional.of(objectMapper.readTree(response.body()));
        } catch (IOException e) {
            log.warn("External provider call to {} failed: {}", redact(url), e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("External provider call to {} was interrupted", redact(url));
            return Optional.empty();
        }
    }

    private String redact(String url) {
        return SENSITIVE_QUERY_PARAM.matcher(url).replaceAll("$1***");
    }
}
