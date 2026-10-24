package com.leaderboard.controller;

import com.leaderboard.dto.UserResponse;
import com.leaderboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/{id}")
    public Mono<UserResponse> getUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found")));
    }
}