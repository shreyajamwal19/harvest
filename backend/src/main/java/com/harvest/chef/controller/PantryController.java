package com.harvest.chef.controller;

import com.harvest.chef.pantry.dto.PantryItemRequest;
import com.harvest.chef.pantry.dto.PantryItemResponse;
import com.harvest.chef.pantry.entity.PantryItem;
import com.harvest.chef.pantry.service.PantryService;
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
 * Turns the deterministic pantry intelligence already built for the chat pipeline
 * ({@link PantryService}) into a real, dedicated Pantry page API. The chat commands
 * ("I bought eggs", "remove onions", ...) and this API both read/write the same
 * PantryItem table, so a change made in chat instantly shows up here and vice versa.
 */
@RestController
@RequestMapping("/api/pantry")
@RequiredArgsConstructor
public class PantryController {

    private final PantryService pantryService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<PantryItemResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        List<PantryItemResponse> items = pantryService.listEntities(userId).stream()
                .map(PantryItemResponse::from)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping
    public ResponseEntity<PantryItemResponse> add(@AuthenticationPrincipal UserDetails userDetails,
                                                    @Valid @RequestBody PantryItemRequest request) {
        Long userId = resolveUserId(userDetails);
        PantryItem saved = pantryService.addOrRestock(
                userId, request.getIngredientName(), request.getQuantity(), request.getUnit());
        return ResponseEntity.status(HttpStatus.CREATED).body(PantryItemResponse.from(saved));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long itemId) {
        Long userId = resolveUserId(userDetails);
        boolean removed = pantryService.removeById(userId, itemId);
        if (!removed) {
            throw new ResourceNotFoundException("Pantry item not found");
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        pantryService.clear(userId);
        return ResponseEntity.noContent().build();
    }

    private Long resolveUserId(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }
}
