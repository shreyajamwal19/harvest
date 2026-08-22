package com.harvest.chef.controller;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.pantry.service.PantryService;
import com.harvest.chef.personalization.service.UserProfileService;
import com.harvest.chef.planning.dto.MealPlanDay;
import com.harvest.chef.planning.dto.RegenerateDayRequest;
import com.harvest.chef.planning.service.MealPlanningService;
import com.harvest.chef.retrieval.RecipeCategoryClassifier.Category;
import com.harvest.entity.User;
import com.harvest.exception.ResourceNotFoundException;
import com.harvest.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * "Swap this day" on the Meal Plan page. Deterministic and structured - a day swap is exactly
 * the kind of pick MealPlanningService already makes without any LLM involvement, so this skips
 * the chat pipeline entirely rather than trying to phrase it as a message for a detector to
 * parse. Builds a lightweight ConversationContext directly (pantry + profile only) instead of
 * going through ContextAssemblyService, since a day swap has no conversation turn or session of
 * its own to create.
 */
@RestController
@RequestMapping("/api/meal-plan")
@RequiredArgsConstructor
public class MealPlanController {

    private final MealPlanningService mealPlanningService;
    private final PantryService pantryService;
    private final UserProfileService userProfileService;
    private final UserRepository userRepository;

    @PostMapping("/regenerate-day")
    public ResponseEntity<MealPlanDay> regenerateDay(@AuthenticationPrincipal UserDetails userDetails,
                                                       @Valid @RequestBody RegenerateDayRequest request) {
        Long userId = resolveUserId(userDetails);

        ConversationContext context = ConversationContext.builder()
                .userId(userId)
                .pantry(pantryService.loadSnapshot(userId))
                .userProfile(userProfileService.loadSnapshot(userId))
                .build();

        MealPlanDay day = mealPlanningService.regenerateDay(
                context, request.getExcludeTitles(), parseCategory(request.getMealType()));
        if (day == null) {
            throw new ResourceNotFoundException("Couldn't find a different recipe for this day right now");
        }
        return ResponseEntity.ok(day);
    }

    private Category parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Category.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Long resolveUserId(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }
}
