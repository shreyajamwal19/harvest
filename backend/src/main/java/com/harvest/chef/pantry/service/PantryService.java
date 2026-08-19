package com.harvest.chef.pantry.service;

import com.harvest.chef.pantry.dto.PantrySnapshot;
import com.harvest.chef.pantry.entity.PantryItem;
import com.harvest.chef.pantry.repository.PantryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Owns the deterministic pantry: loading a read-only snapshot for a turn,
 * and every mutation (add/restock, consume, remove, clear). Mirrors
 * {@code UserProfileService}'s shape from Phase 6A - same fail-safe
 * philosophy: a pantry outage never breaks recipe recommendations, it
 * just means personalization/pantry-awareness sits out that turn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PantryService {

    private final PantryItemRepository pantryItemRepository;
    private final PantryCategorizer categorizer;

    /** Never throws - see FAILURE_HANDLING. Returns an empty snapshot on any failure. */
    public PantrySnapshot loadSnapshot(Long userId) {
        if (userId == null) {
            return PantrySnapshot.empty(null);
        }
        try {
            var items = pantryItemRepository.findByUserIdOrderByIngredientNameAsc(userId).stream()
                    .map(p -> PantrySnapshot.Item.builder()
                            .ingredientName(p.getIngredientName())
                            .quantity(p.getQuantity())
                            .unit(p.getUnit())
                            .category(p.getCategory())
                            .expiryDate(p.getExpiryDate())
                            .build())
                    .toList();

            log.info("[pantry] read userId={} items={}", userId, items.size());
            return PantrySnapshot.builder().userId(userId).items(items).build();
        } catch (Exception e) {
            log.warn("[pantry] failed to load pantry for userId={} - continuing without it: {}",
                    userId, e.getMessage());
            return PantrySnapshot.empty(userId);
        }
    }

    /**
     * Adds a new item or restocks an existing one. When {@code quantity} is non-null and the
     * item already exists, quantities are summed (a second "I bought eggs" adds to what's
     * already there rather than overwriting it) - matches the RESTOCK behaviour called for.
     * Returns the saved entity so callers (e.g. the Pantry REST API) can render it immediately
     * without a second read.
     */
    @Transactional
    public PantryItem addOrRestock(Long userId, String ingredientName, Double quantity, String unit) {
        if (userId == null || ingredientName == null || ingredientName.isBlank()) {
            return null;
        }
        String normalized = normalize(ingredientName);
        Optional<PantryItem> existing = pantryItemRepository.findByUserIdAndIngredientName(userId, normalized);

        PantryItem item = existing.orElseGet(() -> PantryItem.builder()
                .userId(userId)
                .ingredientName(normalized)
                .category(categorizer.categorize(normalized))
                .purchaseDate(LocalDate.now())
                .build());

        if (quantity != null) {
            double base = existing.isPresent() && item.getQuantity() != null ? item.getQuantity() : 0.0;
            item.setQuantity(base + quantity);
            if (unit != null && !unit.isBlank()) {
                item.setUnit(unit);
            }
        }
        PantryItem saved = pantryItemRepository.save(item);
        log.info("[pantry] add/restock userId={} ingredient='{}' quantity={} unit={}",
                userId, normalized, saved.getQuantity(), saved.getUnit());
        return saved;
    }

    /** Full entity list (with IDs), ordered - the basis for the Pantry page. Never throws. */
    public List<PantryItem> listEntities(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            return pantryItemRepository.findByUserIdOrderByIngredientNameAsc(userId);
        } catch (Exception e) {
            log.warn("[pantry] failed to list pantry for userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /** Ownership-checked delete by id, for the Pantry page's remove button. Returns false if not found/owned. */
    @Transactional
    public boolean removeById(Long userId, Long itemId) {
        if (userId == null || itemId == null) {
            return false;
        }
        Optional<PantryItem> item = pantryItemRepository.findByIdAndUserId(itemId, userId);
        if (item.isEmpty()) {
            return false;
        }
        pantryItemRepository.delete(item.get());
        log.info("[pantry] removeById userId={} itemId={}", userId, itemId);
        return true;
    }

    /**
     * Sets (not adds to) an item's quantity directly - the Pantry page's +/- stepper. Returns
     * null if the item doesn't exist or isn't owned by this user, so the controller can 404
     * rather than silently no-op.
     */
    @Transactional
    public PantryItem updateQuantity(Long userId, Long itemId, Double quantity) {
        if (userId == null || itemId == null || quantity == null) {
            return null;
        }
        Optional<PantryItem> found = pantryItemRepository.findByIdAndUserId(itemId, userId);
        if (found.isEmpty()) {
            return null;
        }
        PantryItem item = found.get();
        item.setQuantity(quantity);
        PantryItem saved = pantryItemRepository.save(item);
        log.info("[pantry] updateQuantity userId={} itemId={} quantity={}", userId, itemId, quantity);
        return saved;
    }

    /** Fully removes an item ("remove onions", "no more cheese"), regardless of quantity. */
    @Transactional
    public int remove(Long userId, String ingredientFragment) {
        if (userId == null || ingredientFragment == null || ingredientFragment.isBlank()) {
            return 0;
        }
        int removed = pantryItemRepository.deleteByUserIdAndIngredientNameContaining(userId, normalize(ingredientFragment));
        log.info("[pantry] remove userId={} fragment='{}' removed={}", userId, ingredientFragment, removed);
        return removed;
    }

    /**
     * "I used the milk" / "I ran out of rice" - consumes the item. Without a stated amount this
     * is treated the same as {@link #remove}: the user is telling us it's gone, not by how much.
     */
    @Transactional
    public int consume(Long userId, String ingredientFragment) {
        return remove(userId, ingredientFragment);
    }

    @Transactional
    public void clear(Long userId) {
        int removed = pantryItemRepository.deleteAllByUserId(userId);
        log.info("[pantry] cleared userId={} itemsRemoved={}", userId, removed);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
