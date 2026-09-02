package com.harvest.chef.controller;

import com.harvest.chef.personalization.dto.UserPreferenceResponse;
import com.harvest.chef.personalization.service.UserProfileService;
import com.harvest.entity.User;
import com.harvest.exception.ResourceNotFoundException;
import com.harvest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Previously the ONLY way to view or manage learned preferences was chat commands ("show my
 * preferences", "forget spicy food", "reset my profile") - there was no REST API and no page
 * for it at all, despite the personalization system (cuisine, serving size, cooking duration,
 * dietary restrictions, ...) being extensively built out. Mirrors PantryController's shape:
 * this and the chat commands both read/write the same UserPreference table, so a change made
 * in chat instantly shows up here and vice versa.
 */
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final UserProfileService userProfileService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<UserPreferenceResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        List<UserPreferenceResponse> preferences = userProfileService.listEntities(userId).stream()
                .map(UserPreferenceResponse::from)
                .toList();
        return ResponseEntity.ok(preferences);
    }

    @DeleteMapping("/{preferenceId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long preferenceId) {
        Long userId = resolveUserId(userDetails);
        boolean removed = userProfileService.deleteById(userId, preferenceId);
        if (!removed) {
            throw new ResourceNotFoundException("Preference not found");
        }
        return ResponseEntity.noContent().build();
    }

    /** Same as the "reset my profile" chat command - clears every learned preference. */
    @DeleteMapping
    public ResponseEntity<Void> resetAll(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        userProfileService.resetProfile(userId);
        return ResponseEntity.noContent().build();
    }

    private Long resolveUserId(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }
}
