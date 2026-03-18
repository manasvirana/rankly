package com.leaderboard.controller;

import com.leaderboard.dto.Requests;
import com.leaderboard.model.User;
import com.leaderboard.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> createUser(@Valid @RequestBody Requests.CreateUser request) {
        return userRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) return Mono.error(
                            new IllegalArgumentException("Username already taken: " + request.getUsername()));

                    User user = User.builder()
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .createdAt(Instant.now())
                            .build();
                    return userRepository.save(user);
                });
    }

    @GetMapping("/{id}")
    public Mono<User> getUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found")));
    }
}
