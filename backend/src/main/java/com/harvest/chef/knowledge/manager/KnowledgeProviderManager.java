package com.harvest.chef.knowledge.manager;

import com.harvest.chef.dto.NutritionInfo;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.knowledge.model.IngredientProfile;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.CookingKnowledgeProvider;
import com.harvest.chef.knowledge.provider.IngredientIntelligenceProvider;
import com.harvest.chef.knowledge.provider.KnowledgeProvider;
import com.harvest.chef.knowledge.provider.NutritionKnowledgeProvider;
import com.harvest.chef.knowledge.provider.RecipeKnowledgeProvider;
import com.harvest.chef.knowledge.provider.UserMemoryKnowledgeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The single entry point for every knowledge request. Selects which
 * providers to run, runs them in parallel, isolates failures so one bad
 * provider never breaks retrieval, and merges/normalizes the results. The
 * Retrieval Orchestrator calls this and only this - it never talks to a
 * provider directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeProviderManager {

    private static final long PROVIDER_TIMEOUT_SECONDS = 10;

    private final KnowledgeProviderRegistry registry;
    private final ExecutorService knowledgeProviderExecutor;

    // ---------------------------------------------------------------- recipes

    public List<RecipeCandidate> retrieveRecipes(String query, boolean planWantsExternal) {
        List<RecipeKnowledgeProvider> locals = registry.getRecipeProviders().stream()
                .filter(RecipeKnowledgeProvider::isLocal)
                .toList();
        List<RecipeKnowledgeProvider> externals = registry.getRecipeProviders().stream()
                .filter(p -> !p.isLocal())
                .toList();

        List<ProviderResult<List<RecipeCandidate>>> results = new ArrayList<>();
        results.addAll(executeAll(locals, p -> p.retrieve(query)));

        boolean localSucceeded = results.stream().anyMatch(ProviderResult::isSuccess);
        boolean localHasResults = results.stream()
                .filter(ProviderResult::isSuccess)
                .anyMatch(r -> r.getData() != null && !r.getData().isEmpty());

        // Failover: local failed or came back empty -> automatically try external.
        // Business-driven expansion: the retrieval plan itself asked for external too.
        boolean shouldUseExternal = !localSucceeded || !localHasResults || planWantsExternal;
        if (shouldUseExternal && !externals.isEmpty()) {
            results.addAll(executeAll(externals, p -> p.retrieve(query)));
        }

        List<RecipeCandidate> merged = mergeRecipes(results);
        logObservability("recipes", results, merged.size());
        return merged;
    }

    // ---------------------------------------------------------------- nutrition

    public List<NutritionInfo> retrieveNutrition(List<String> ingredientNames) {
        List<ProviderResult<List<NutritionInfo>>> results =
                executeAll(registry.getNutritionProviders(), p -> p.retrieve(ingredientNames));

        List<NutritionInfo> merged = new ArrayList<>();
        for (ProviderResult<List<NutritionInfo>> result : results) {
            if (result.isSuccess() && result.getData() != null) {
                merged.addAll(result.getData());
            }
        }
        logObservability("nutrition", results, merged.size());
        return merged;
    }

    // ---------------------------------------------------------------- ingredient intelligence

    public List<IngredientProfile> retrieveIngredientIntelligence(List<String> ingredientNames) {
        List<ProviderResult<List<IngredientProfile>>> results =
                executeAll(registry.getIngredientIntelligenceProviders(), p -> p.retrieve(ingredientNames));

        List<IngredientProfile> merged = new ArrayList<>();
        for (ProviderResult<List<IngredientProfile>> result : results) {
            if (result.isSuccess() && result.getData() != null) {
                merged.addAll(result.getData());
            }
        }
        logObservability("ingredient-intelligence", results, merged.size());
        return merged;
    }

    // ---------------------------------------------------------------- cooking knowledge

    public String retrieveCookingKnowledge(String question, String interpretedGoal) {
        List<ProviderResult<String>> results = executeAll(
                registry.getCookingKnowledgeProviders(), p -> p.retrieve(question, interpretedGoal));

        String best = results.stream()
                .filter(ProviderResult::isSuccess)
                .max((a, b) -> Double.compare(a.getConfidence(), b.getConfidence()))
                .map(ProviderResult::getData)
                .orElse(null);

        logObservability("cooking-knowledge", results, best == null ? 0 : 1);
        return best;
    }

    // ---------------------------------------------------------------- user memory

    public List<String> retrieveUserMemory(Long userId, Long excludingSessionId) {
        List<ProviderResult<List<String>>> results =
                executeAll(registry.getUserMemoryProviders(), p -> p.retrieve(userId, excludingSessionId));

        List<String> merged = new ArrayList<>();
        for (ProviderResult<List<String>> result : results) {
            if (result.isSuccess() && result.getData() != null) {
                merged.addAll(result.getData());
            }
        }
        logObservability("user-memory", results, merged.size());
        return merged;
    }

    // ---------------------------------------------------------------- shared execution + merging

    /**
     * Runs every available provider in parallel, isolates individual
     * failures (timeout, exception) as a failed ProviderResult rather than
     * propagating them, and waits for all to finish or time out.
     */
    private <P extends KnowledgeProvider, T> List<ProviderResult<T>> executeAll(
            List<P> providers, Function<P, ProviderResult<T>> invocation) {

        List<P> available = providers.stream().filter(this::safeIsAvailable).toList();
        if (available.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<ProviderResult<T>>> futures = available.stream()
                .map(provider -> runAsync(() -> invokeSafely(provider, invocation)))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<ProviderResult<T>> results = new ArrayList<>();
        for (CompletableFuture<ProviderResult<T>> future : futures) {
            results.add(future.join());
        }
        return results;
    }

    private <T> CompletableFuture<ProviderResult<T>> runAsync(Supplier<ProviderResult<T>> supplier) {
        return CompletableFuture.supplyAsync(supplier, knowledgeProviderExecutor)
                .orTimeout(PROVIDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(throwable -> ProviderResult.<T>failure(
                        "unknown", describeFailure(throwable), PROVIDER_TIMEOUT_SECONDS * 1000));
    }

    private <P extends KnowledgeProvider, T> ProviderResult<T> invokeSafely(
            P provider, Function<P, ProviderResult<T>> invocation) {
        long start = System.currentTimeMillis();
        try {
            return invocation.apply(provider);
        } catch (Exception e) {
            log.warn("Knowledge provider '{}' threw an exception: {}", provider.getName(), e.getMessage());
            return ProviderResult.failure(provider.getName(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private boolean safeIsAvailable(KnowledgeProvider provider) {
        try {
            return provider.isAvailable();
        } catch (Exception e) {
            log.warn("Availability check failed for provider '{}': {}", provider.getName(), e.getMessage());
            return false;
        }
    }

    private String describeFailure(Throwable throwable) {
        Throwable cause = throwable instanceof TimeoutException ? throwable : throwable.getCause();
        return cause == null ? throwable.getMessage() : cause.getMessage();
    }

    /**
     * Deduplicates recipe candidates by normalized title across every
     * provider that contributed, preferring the first (highest-priority)
     * occurrence while preserving provider attribution on each surviving
     * candidate.
     */
    private List<RecipeCandidate> mergeRecipes(List<ProviderResult<List<RecipeCandidate>>> results) {
        Map<String, RecipeCandidate> byNormalizedTitle = new LinkedHashMap<>();
        for (ProviderResult<List<RecipeCandidate>> result : results) {
            if (!result.isSuccess() || result.getData() == null) {
                continue;
            }
            for (RecipeCandidate candidate : result.getData()) {
                String key = normalize(candidate.getTitle());
                byNormalizedTitle.putIfAbsent(key, candidate);
            }
        }
        return new ArrayList<>(byNormalizedTitle.values());
    }

    private String normalize(String title) {
        return title == null ? "" : title.trim().toLowerCase();
    }

    private void logObservability(String category, List<? extends ProviderResult<?>> results, int mergedCount) {
        for (ProviderResult<?> result : results) {
            if (result.isSuccess()) {
                log.info("[knowledge:{}] provider={} confidence={} completeness={} latencyMs={}",
                        category, result.getProviderName(), result.getConfidence(),
                        result.getCompleteness(), result.getLatencyMs());
            } else {
                log.warn("[knowledge:{}] provider={} FAILED error={} latencyMs={}",
                        category, result.getProviderName(), result.getErrorMessage(), result.getLatencyMs());
            }
        }
        log.info("[knowledge:{}] merged result count={}", category, mergedCount);
    }
}
