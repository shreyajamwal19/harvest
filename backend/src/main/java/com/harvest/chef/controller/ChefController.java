package com.harvest.chef.controller;

import com.harvest.chef.dto.ChatRequest;
import com.harvest.chef.dto.ChatResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.service.ChefChatRateLimiter;
import com.harvest.chef.service.ChefOrchestrator;
import com.harvest.entity.User;
import com.harvest.exception.RateLimitExceededException;
import com.harvest.exception.ResourceNotFoundException;
import com.harvest.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chef")
@RequiredArgsConstructor
@Slf4j
public class ChefController {

    private final ChefOrchestrator chefOrchestrator;
    private final UserRepository userRepository;
    private final ChefChatRateLimiter chefChatRateLimiter;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@AuthenticationPrincipal UserDetails userDetails,
                                              @Valid @RequestBody ChatRequest request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Checked before the try/catch below on purpose: that catch-all deliberately turns any
        // failure into a normal 200 in-conversation response, which would silently swallow the
        // 429 a rate-limited client needs to actually see and back off from.
        if (chefChatRateLimiter.isRateLimited(user.getId())) {
            throw new RateLimitExceededException(
                    "You're sending messages a bit fast - please wait a moment and try again.");
        }

        // The Chef Brain pipeline is the single most complex call in the app (retrieval,
        // scoring, personalization, session state, and multiple LLM calls all in one turn).
        // Any unexpected failure anywhere in that chain should read to the person as "Chef
        // Brain had trouble" - a normal, in-conversation response - never a raw 500 page.
        // The real exception is still logged for debugging; it's just never the thing the
        // person sees.
        try {
            ChatResponse response =
                    chefOrchestrator.handle(user.getId(), request.getSessionId(), request.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Chef Brain request failed unexpectedly for userId={}", user.getId(), e);
            ChatResponse fallback = ChatResponse.builder()
                    .sessionId(request.getSessionId())
                    .responseType(ChefResponseType.HONEST_NON_ANSWER)
                    .message("Something went wrong on my end just now. Please try that again in a moment.")
                    .build();
            return ResponseEntity.ok(fallback);
        }
    }
}
