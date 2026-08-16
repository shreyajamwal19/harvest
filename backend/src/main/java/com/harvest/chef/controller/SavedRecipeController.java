package com.harvest.chef.controller;

import com.harvest.chef.personalization.dto.SaveRecipeRequest;
import com.harvest.chef.personalization.dto.SavedRecipeResponse;
import com.harvest.chef.personalization.service.SavedRecipeService;
import com.harvest.entity.User;
import com.harvest.exception.ResourceNotFoundException;
import com.harvest.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Turns the always-modeled-but-never-triggered {@code HistoryEventType.SAVED} event into a
 * real "save this recipe" surface, backed by {@link SavedRecipeService}.
 */
@RestController
@RequestMapping("/api/saved-recipes")
@RequiredArgsConstructor
public class SavedRecipeController {

    private final SavedRecipeService savedRecipeService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<SavedRecipeResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(savedRecipeService.list(resolveUserId(userDetails)));
    }

    @PostMapping
    public ResponseEntity<SavedRecipeResponse> save(@AuthenticationPrincipal UserDetails userDetails,
                                                      @Valid @RequestBody SaveRecipeRequest request) {
        SavedRecipeResponse saved = savedRecipeService.save(resolveUserId(userDetails), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        boolean removed = savedRecipeService.removeById(resolveUserId(userDetails), id);
        if (!removed) {
            throw new ResourceNotFoundException("Saved recipe not found");
        }
        return ResponseEntity.noContent().build();
    }

    private Long resolveUserId(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }
}
