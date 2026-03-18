package com.leaderboard.controller;

import com.leaderboard.dto.LeaderboardUpdate;
import com.leaderboard.dto.Requests;
import com.leaderboard.model.Contest;
import com.leaderboard.repository.ContestRepository;
import com.leaderboard.service.LeaderboardBroadcastService;
import com.leaderboard.service.RedisLeaderboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestRepository contestRepository;
    private final RedisLeaderboardService redisLeaderboard;
    private final LeaderboardBroadcastService broadcastService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Contest> createContest(@Valid @RequestBody Requests.CreateContest request) {
        Contest contest = Contest.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status("ACTIVE")
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .createdAt(Instant.now())
                .build();
        return contestRepository.save(contest);
    }

    @GetMapping
    public Flux<Contest> listContests(@RequestParam(defaultValue = "ACTIVE") String status) {
        return contestRepository.findByStatus(status);
    }

    @GetMapping("/{id}")
    public Mono<Contest> getContest(@PathVariable UUID id) {
        return contestRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Contest not found")));
    }

    @PostMapping("/{id}/end")
    public Mono<Contest> endContest(@PathVariable UUID id) {
        String contestId = id.toString();

        return contestRepository.endContest(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Contest not found")))
                .flatMap(contest ->
                        redisLeaderboard.getTopEntries(contestId, 10)
                                .collectList()
                                .doOnNext(topEntries -> {
                                    LeaderboardUpdate finalUpdate = LeaderboardUpdate.builder()
                                            .contestId(contestId)
                                            .topEntries(topEntries)
                                            .updatedAt(Instant.now())
                                            .eventType("CONTEST_ENDED")
                                            .build();
                                    broadcastService.broadcast(contestId, finalUpdate);
                                    broadcastService.closeSink(contestId);
                                })
                                .thenReturn(contest)
                );
    }
}
