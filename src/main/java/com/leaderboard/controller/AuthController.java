package com.leaderboard.controller;

import com.leaderboard.dto.AuthRequests;
import com.leaderboard.dto.AuthResponse;
import com.leaderboard.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequests.Register request) {
        AuthResponse response = authService.register(request).block();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequests.Login request) {
        AuthResponse response = authService.login(request).block();
        return ResponseEntity.ok(response);
    }
}