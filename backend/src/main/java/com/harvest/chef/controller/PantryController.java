package com.harvest.chef.controller;

import com.harvest.chef.pantry.dto.PantryExpiryUpdateRequest;
import com.harvest.chef.pantry.dto.PantryItemRequest;
import com.harvest.chef.pantry.dto.PantryItemResponse;
import com.harvest.chef.pantry.dto.PantryQuantityUpdateRequest;
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
                userId, request.getIngredientName(), request.getQuantity(), request.getUnit(),
                request.getExpiryDate());
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

    /**
     * Sets an item's quantity directly (the Pantry page's +/- stepper). A quantity of 0 or
     * below removes the item outright rather than leaving a stale "0 units" row around -
     * matches the same "ran out" semantics the chat pantry commands already use.
     */
    @PatchMapping("/{itemId}")
    public ResponseEntity<PantryItemResponse> updateQuantity(@AuthenticationPrincipal UserDetails userDetails,
                                                               @PathVariable Long itemId,
                                                               @Valid @RequestBody PantryQuantityUpdateRequest request) {
        Long userId = resolveUserId(userDetails);

        if (request.getQuantity() <= 0) {
            boolean removed = pantryService.removeById(userId, itemId);
            if (!removed) {
                throw new ResourceNotFoundException("Pantry item not found");
            }
            return ResponseEntity.noContent().build();
        }

        PantryItem updated = pantryService.updateQuantity(userId, itemId, request.getQuantity());
        if (updated == null) {
            throw new ResourceNotFoundException("Pantry item not found");
        }
        return ResponseEntity.ok(PantryItemResponse.from(updated));
    }

    /**
     * Sets or clears (null expiryDate) a single item's expiry - the only place in the whole app
     * this was ever settable before now (not even via chat). Separate endpoint from the quantity
     * PATCH above rather than folded into it: quantity<=0 there means "remove the item", a
     * completely different intent than "I don't want to track this item's expiry anymore".
     */
    @PatchMapping("/{itemId}/expiry")
    public ResponseEntity<PantryItemResponse> updateExpiry(@AuthenticationPrincipal UserDetails userDetails,
                                                             @PathVariable Long itemId,
                                                             @RequestBody PantryExpiryUpdateRequest request) {
        Long userId = resolveUserId(userDetails);
        PantryItem updated = pantryService.updateExpiryDate(userId, itemId, request.getExpiryDate());
        if (updated == null) {
            throw new ResourceNotFoundException("Pantry item not found");
        }
        return ResponseEntity.ok(PantryItemResponse.from(updated));
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
