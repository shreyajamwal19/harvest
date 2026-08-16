package com.harvest.chef.controller;

import com.harvest.chef.personalization.dto.MarkCookedRequest;
import com.harvest.chef.personalization.entity.HistoryEventType;
import com.harvest.chef.personalization.service.CookingHistoryService;
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

import java.util.Map;

/**
 * Activates the previously-unused COOKED history event, the same way SavedRecipeController
 * activated SAVED. Called by Cooking Mode when a user finishes a recipe - this feeds Smart
 * Variety (RecipeScoringEngine) so a just-cooked dish is less likely to be re-suggested
 * immediately, and REPEATED detection already treats it like any other title occurrence.
 */
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeHistoryController {

    private final CookingHistoryService cookingHistoryService;
    private final UserRepository userRepository;

    @PostMapping("/cooked")
    public ResponseEntity<Map<String, String>> markCooked(@AuthenticationPrincipal UserDetails userDetails,
                                                            @Valid @RequestBody MarkCookedRequest request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        cookingHistoryService.recordEvent(user.getId(), request.getTitle(), HistoryEventType.COOKED);
        return ResponseEntity.ok(Map.of("message", "Marked as cooked"));
    }
}
