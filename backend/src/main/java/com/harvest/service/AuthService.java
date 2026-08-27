package com.harvest.service;

import com.harvest.dto.AuthResponse;
import com.harvest.dto.LoginRequest;
import com.harvest.dto.SignupRequest;
import com.harvest.dto.UserDto;
import com.harvest.entity.User;
import com.harvest.exception.AccountTemporarilyLockedException;
import com.harvest.exception.DuplicateResourceException;
import com.harvest.exception.ResourceNotFoundException;
import com.harvest.repository.UserRepository;
import com.harvest.security.JwtUtil;
import com.harvest.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public AuthResult signup(SignupRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Guards against the race where two signups for the same email are submitted
            // concurrently and both pass the existsByEmail check above; the DB's unique
            // constraint on email is the real source of truth.
            throw new DuplicateResourceException("An account with this email already exists");
        }

        String token = jwtUtil.generateToken(savedUser.getEmail());
        log.info("New user registered: {}", savedUser.getEmail());

        AuthResponse body = AuthResponse.builder()
                .user(UserDto.from(savedUser))
                .message("Account created successfully")
                .expiresAt(Instant.now().plusMillis(jwtUtil.getExpirationMs()))
                .build();

        return new AuthResult(token, body);
    }

    public AuthResult login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (loginAttemptService.isLocked(normalizedEmail)) {
            throw new AccountTemporarilyLockedException(
                    "Too many failed login attempts. Please try again in a few minutes.");
        }

        // BadCredentialsException (wrong password) and UsernameNotFoundException (unknown
        // email, wrapped as BadCredentialsException by default so we don't leak which one
        // failed) both propagate up and are mapped to 401 by GlobalExceptionHandler - but are
        // first recorded as a failed attempt so repeated guessing eventually locks out.
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword()));
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(normalizedEmail);
            throw e;
        }
        loginAttemptService.recordSuccess(normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String token = jwtUtil.generateToken(authentication.getName());
        log.info("User logged in: {}", user.getEmail());

        AuthResponse body = AuthResponse.builder()
                .user(UserDto.from(user))
                .message("Login successful")
                .expiresAt(Instant.now().plusMillis(jwtUtil.getExpirationMs()))
                .build();

        return new AuthResult(token, body);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Pairs the raw JWT (which the controller puts in an httpOnly cookie) with the
     * response body that actually gets returned to the client.
     */
    public record AuthResult(String token, AuthResponse body) {
    }
}
