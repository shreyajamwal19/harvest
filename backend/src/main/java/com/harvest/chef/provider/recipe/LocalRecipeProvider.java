package com.harvest.chef.provider.recipe;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.entity.Recipe;
import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.RecipeKnowledgeProvider;
import com.harvest.chef.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wraps the local Recipe table - 231k+ imported Food.com rows. Splits the
 * incoming query into individual keyword tokens and matches candidates
 * against ANY of them: a single combined substring match ("eggs cheese"
 * as one literal phrase) would almost never hit a real ingredient line,
 * so token-level OR matching is what actually makes this usable against a
 * large imported dataset.
 *
 * Pulls a generously large candidate pool (CANDIDATE_LIMIT) since real
 * ranking/scoring happens afterwards in
 * {@link com.harvest.chef.retrieval.RecipeEvaluationService} - this
 * provider's job is recall, not precision.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalRecipeProvider implements RecipeKnowledgeProvider {

    /** Large enough to give the evaluation stage real options across 200k+ rows,
     *  small enough to stay fast and keep memory/latency bounded. */
    private static final int CANDIDATE_LIMIT = 300;

    /** Used only when there's no real search signal at all (e.g. "I'm hungry") -
     *  an honest browse of the catalog rather than a search that would match nothing. */
    private static final int BROWSE_LIMIT = 60;

    private final RecipeRepository recipeRepository;

    @Override
    public ProviderResult<List<RecipeCandidate>> retrieve(String query) {
        long start = System.currentTimeMillis();
        try {
            List<String> tokens = tokenize(query);

            List<Recipe> matches = tokens.isEmpty()
                    ? recipeRepository.findAll(PageRequest.of(0, BROWSE_LIMIT)).getContent()
                    : recipeRepository.searchByIngredientTokens(tokens, CANDIDATE_LIMIT);

            List<RecipeCandidate> candidates = matches.stream().map(this::toCandidate).toList();

            return ProviderResult.<List<RecipeCandidate>>builder()
                    .data(candidates)
                    .success(true)
                    .providerName(getName())
                    .confidence(candidates.isEmpty() ? 0.0 : 0.75)
                    .completeness(candidates.isEmpty() ? 0.0 : 1.0)
                    .latencyMs(System.currentTimeMillis() - start)
                    .reliability(getReliability())
                    .retrievedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.warn("Local recipe provider failed: {}", e.getMessage());
            return ProviderResult.failure(getName(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * Splits an already-normalized query (e.g. "eggs cheese", "quick
     * breakfast") into individual search tokens - matching must happen
     * per-token, not as one combined literal phrase, or multi-word/
     * multi-ingredient queries never match anything in the imported
     * dataset.
     */
    private List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            String cleaned = word.replaceAll("[^a-z0-9]", "");
            if (cleaned.length() >= 2) {
                tokens.add(cleaned);
            }
        }
        return tokens;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public KnowledgeProviderType getType() {
        return KnowledgeProviderType.RECIPE;
    }

    @Override
    public String getName() {
        return "local";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ProviderHealth healthStatus() {
        try {
            recipeRepository.count();
            return ProviderHealth.UP;
        } catch (Exception e) {
            return ProviderHealth.DOWN;
        }
    }

    @Override
    public double getReliability() {
        return 0.95;
    }

    private RecipeCandidate toCandidate(Recipe recipe) {
        return RecipeCandidate.builder()
                .title(recipe.getTitle())
                .description(recipe.getDescription())
                .servings(recipe.getServings())
                .ingredients(recipe.getIngredients())
                .steps(recipe.getSteps())
                .source(getName())
                .build();
    }
}
