package com.leaderboard.controller;

import com.leaderboard.dto.LeaderboardEntry;
import com.leaderboard.dto.Requests;
import com.leaderboard.service.ScoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/contests/{contestId}")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @PostMapping("/scores")
    public Mono<LeaderboardEntry> submitScore(
            @PathVariable String contestId,
            @Valid @RequestBody Requests.SubmitScore request) {
        return scoreService.submitScore(contestId, request);
    }

    @GetMapping("/leaderboard")
    public Flux<LeaderboardEntry> getLeaderboard(
            @PathVariable String contestId,
            @RequestParam(defaultValue = "50") int limit) {
        return scoreService.getLeaderboard(contestId, limit);
    }

    @GetMapping("/rank/{userId}")
    public Mono<LeaderboardEntry> getUserRank(
            @PathVariable String contestId,
            @PathVariable String userId) {
        return scoreService.getUserRank(contestId, userId);
    }
}
