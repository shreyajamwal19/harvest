package com.harvest.controller;

import com.harvest.dto.AuthResponse;
import com.harvest.dto.LoginRequest;
import com.harvest.dto.SignupRequest;
import com.harvest.security.CookieUtil;
import com.harvest.security.JwtUtil;
import com.harvest.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final JwtUtil jwtUtil;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthService.AuthResult result = authService.signup(request);
        return withAuthCookie(result, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request);
        return withAuthCookie(result, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        ResponseCookie expiredCookie = cookieUtil.buildExpiredCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(Map.of("message", "Logged out successfully"));
    }

    private ResponseEntity<AuthResponse> withAuthCookie(AuthService.AuthResult result, HttpStatus status) {
        ResponseCookie cookie = cookieUtil.buildAuthCookie(result.token(), jwtUtil.getExpirationMs());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.body());
    }
}
