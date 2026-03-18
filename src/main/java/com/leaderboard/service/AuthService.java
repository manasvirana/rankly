package com.leaderboard.service;

import com.leaderboard.dto.AuthRequests;
import com.leaderboard.dto.AuthResponse;
import com.leaderboard.model.User;
import com.leaderboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public Mono<AuthResponse> register(AuthRequests.Register request) {
        return userRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) return Mono.error(
                            new IllegalArgumentException("Username already taken"));

                    User user = User.builder()
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .role("USER")
                            .createdAt(Instant.now())
                            .build();

                    return userRepository.save(user);
                })
                .map(user -> {
                    String token = jwtService.generateToken(user.getUsername(), user.getRole());
                    return new AuthResponse(
                            token,
                            user.getId().toString(),
                            user.getUsername(),
                            user.getRole()
                    );
                });
    }

    public Mono<AuthResponse> login(AuthRequests.Login request) {
        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("Invalid username or password")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(
                                new IllegalArgumentException("Invalid username or password"));
                    }
                    String token = jwtService.generateToken(user.getUsername(), user.getRole());
                    return Mono.just(new AuthResponse(
                            token,
                            user.getId().toString(),
                            user.getUsername(),
                            user.getRole()
                    ));
                });
    }
}