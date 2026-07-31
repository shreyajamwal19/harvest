package com.harvest.chef.retrieval;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RecipeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The final fallback in the priority chain, used only when Recipe
 * Evaluation kept nothing (which - since evaluation no longer rejects
 * candidates - only happens when retrieval itself found nothing).
 *
 * No LLM call: there's nothing to fall back to without a source of
 * grounded data, so this honestly returns no recipes rather than
 * fabricating one.
 */
@Service
@Slf4j
public class RecipeGenerationService {

    public List<RecipeResponse> generate(List<RecipeCandidate> inspiration) {
        log.info("[recipe-generation] {} inspiration candidate(s) available - no LLM to generate from, "
                + "returning no recipes", inspiration == null ? 0 : inspiration.size());
        return List.of();
    }
}
