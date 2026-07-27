package com.harvest.controller;

import com.harvest.dto.SessionResponse;
import com.harvest.dto.UserDto;
import com.harvest.entity.User;
import com.harvest.exception.ResourceNotFoundException;
import com.harvest.repository.UserRepository;
import com.harvest.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

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
}
