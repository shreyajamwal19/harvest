package com.harvest.chef.controller;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.ShowcaseRecipeResponse;
import com.harvest.chef.provider.external.ExternalRecipeApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The one place the public (logged-out) homepage is allowed to touch: a single real dish,
 * photo included, sourced from the same {@link ExternalRecipeApiClient}s the authenticated
 * Chef Brain pipeline already uses. Never invents a recipe or an image - if every provider
 * comes back empty, the homepage simply renders without a photo rather than faking one.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicShowcaseController {

    // A rotating handful of genuinely appetizing, broad search terms - varies the homepage
    // rather than always showing the same dish, without needing any personalization signal
    // (there is none - this endpoint is unauthenticated by design).
    private static final List<String> SHOWCASE_QUERIES = List.of(
            "chicken", "pasta", "salmon", "curry", "soup", "salad", "tacos", "roast", "noodles"
    );

    private final List<ExternalRecipeApiClient> externalRecipeApiClients;

    @GetMapping("/showcase")
    public ResponseEntity<ShowcaseRecipeResponse> showcase() {
        if (externalRecipeApiClients.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        String query = SHOWCASE_QUERIES.get(ThreadLocalRandom.current().nextInt(SHOWCASE_QUERIES.size()));

        for (ExternalRecipeApiClient client : externalRecipeApiClients) {
            Optional<RecipeCandidate> withImage = safeSearch(client, query).stream()
                    .filter(candidate -> candidate.getImageUrl() != null && !candidate.getImageUrl().isBlank())
                    .findFirst();
            if (withImage.isPresent()) {
                RecipeCandidate candidate = withImage.get();
                return ResponseEntity.ok(ShowcaseRecipeResponse.builder()
                        .title(candidate.getTitle())
                        .description(candidate.getDescription())
                        .imageUrl(candidate.getImageUrl())
                        .source(candidate.getSource())
                        .build());
            }
        }

        return ResponseEntity.noContent().build();
    }

    private List<RecipeCandidate> safeSearch(ExternalRecipeApiClient client, String query) {
        try {
            return client.search(query);
        } catch (Exception e) {
            log.warn("Showcase search via '{}' failed: {}", client.apiName(), e.getMessage());
            return List.of();
        }
    }
}
