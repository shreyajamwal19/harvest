package com.harvest.controller;

import com.harvest.chef.pantry.dto.PantryItemResponse;
import com.harvest.chef.pantry.service.PantryService;
import com.harvest.chef.personalization.entity.HistoryEventType;
import com.harvest.chef.personalization.repository.RecipeHistoryRepository;
import com.harvest.chef.personalization.dto.RecipeHistoryEntryResponse;
import com.harvest.chef.personalization.dto.SavedRecipeResponse;
import com.harvest.chef.personalization.dto.UserPreferenceResponse;
import com.harvest.chef.personalization.service.SavedRecipeService;
import com.harvest.chef.personalization.service.UserProfileService;
import com.harvest.dto.ChangePasswordRequest;
import com.harvest.dto.DataExportResponse;
import com.harvest.dto.DeleteAccountRequest;
import com.harvest.dto.SessionResponse;
import com.harvest.dto.UserDto;
import com.harvest.entity.User;
import com.harvest.exception.ResourceNotFoundException;
import com.harvest.repository.UserRepository;
import com.harvest.security.CookieUtil;
import com.harvest.security.JwtAuthFilter;
import com.harvest.service.AccountDeletionService;
import com.harvest.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final AccountDeletionService accountDeletionService;
    private final CookieUtil cookieUtil;
    private final PantryService pantryService;
    private final SavedRecipeService savedRecipeService;
    private final RecipeHistoryRepository recipeHistoryRepository;
    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<SessionResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails,
                                                            HttpServletRequest request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Instant expiresAt = (Instant) request.getAttribute(JwtAuthFilter.TOKEN_EXPIRES_AT_ATTRIBUTE);

        SessionResponse response = SessionResponse.builder()
                .user(UserDto.from(user))
                .expiresAt(expiresAt)
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                                                @Valid @RequestBody ChangePasswordRequest request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        authService.changePassword(user.getId(), request);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    /**
     * Access/portability counterpart to DELETE below - everything Harvest stores about this
     * user, bundled as one JSON download. Reuses the exact same read paths every other page
     * already uses (SavedRecipeService.list, PantryService.listEntities, UserProfileService.
     * listEntities, the same COOKED-only history query the History page uses) rather than a
     * separate export-specific query path that could drift out of sync with what's actually
     * stored.
     */
    @GetMapping("/export")
    public ResponseEntity<DataExportResponse> exportData(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DataExportResponse export = DataExportResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .accountCreatedAt(user.getCreatedAt())
                .exportedAt(Instant.now())
                .pantryItems(pantryService.listEntities(user.getId()).stream()
                        .map(PantryItemResponse::from)
                        .toList())
                .savedRecipes(savedRecipeService.list(user.getId()))
                .cookingHistory(recipeHistoryRepository
                        .findTop50ByUserIdAndEventTypeOrderByCreatedAtDesc(user.getId(), HistoryEventType.COOKED)
                        .stream()
                        .map(RecipeHistoryEntryResponse::from)
                        .toList())
                .preferences(userProfileService.listEntities(user.getId()).stream()
                        .map(UserPreferenceResponse::from)
                        .toList())
                .build();

        return ResponseEntity.ok(export);
    }

    /**
     * Irreversible - requires currentPassword (validated in AccountDeletionService, same
     * proof-of-identity bar as changing a password). Clears the auth cookie on success since the
     * account (and the user this token was issued for) no longer exists.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteAccount(@AuthenticationPrincipal UserDetails userDetails,
                                                               @Valid @RequestBody DeleteAccountRequest request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        accountDeletionService.deleteAccount(user.getId(), request.getCurrentPassword());

        ResponseCookie expiredCookie = cookieUtil.buildExpiredCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(Map.of("message", "Account deleted"));
    }
}
