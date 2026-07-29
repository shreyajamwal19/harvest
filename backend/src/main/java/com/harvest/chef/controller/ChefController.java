package com.harvest.chef.controller;

import com.harvest.chef.dto.ChatRequest;
import com.harvest.chef.dto.ChatResponse;
import com.harvest.chef.service.ChefOrchestrator;
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

@RestController
@RequestMapping("/api/chef")
@RequiredArgsConstructor
public class ChefController {

    private final ChefOrchestrator chefOrchestrator;
    private final UserRepository userRepository;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@AuthenticationPrincipal UserDetails userDetails,
                                              @Valid @RequestBody ChatRequest request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ChatResponse response = chefOrchestrator.handle(user.getId(), request.getSessionId(), request.getMessage());
        return ResponseEntity.ok(response);
    }
}
