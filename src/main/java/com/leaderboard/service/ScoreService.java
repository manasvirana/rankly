package com.leaderboard.service;

import com.leaderboard.dto.LeaderboardEntry;
import com.leaderboard.dto.LeaderboardUpdate;
import com.leaderboard.dto.Requests;
import com.leaderboard.model.Score;
import com.leaderboard.repository.ContestRepository;
import com.leaderboard.repository.ScoreRepository;
import com.leaderboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final RedisLeaderboardService redisLeaderboard;
    private final LeaderboardBroadcastService broadcastService;

    public Mono<LeaderboardEntry> submitScore(String contestId, Requests.SubmitScore request) {
        UUID contestUUID = UUID.fromString(contestId);
        UUID userUUID = UUID.fromString(request.getUserId());

        return contestRepository.findById(contestUUID)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Contest not found: " + contestId)))
                .flatMap(contest -> {
                    if (!"ACTIVE".equals(contest.getStatus())) {
                        return Mono.error(new IllegalStateException("Contest is not active"));
                    }
                    return userRepository.findById(userUUID);
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + request.getUserId())))
                .flatMap(user ->
                        scoreRepository.upsertScore(userUUID, contestUUID, request.getScore())
                                .then(scoreRepository.findByUserIdAndContestId(userUUID, contestUUID))
                                .flatMap(savedScore ->
                                        redisLeaderboard.updateScore(
                                                contestId,
                                                user.getId().toString(),
                                                user.getUsername(),
                                                savedScore.getScore()
                                        ).flatMap(wasUpdated -> {
                                            if (wasUpdated) {
                                                return redisLeaderboard.getTopEntries(contestId, 10)
                                                        .collectList()
                                                        .doOnNext(topEntries -> {
                                                            LeaderboardUpdate update = LeaderboardUpdate.builder()
                                                                    .contestId(contestId)
                                                                    .topEntries(topEntries)
                                                                    .updatedAt(Instant.now())
                                                                    .eventType("SCORE_SUBMITTED")
                                                                    .build();
                                                            broadcastService.broadcast(contestId, update);
                                                        })
                                                        .then(redisLeaderboard.getUserRank(
                                                                contestId,
                                                                user.getId().toString(),
                                                                user.getUsername()
                                                        ));
                                            } else {
                                                return redisLeaderboard.getUserRank(
                                                        contestId,
                                                        user.getId().toString(),
                                                        user.getUsername()
                                                );
                                            }
                                        })
                                )
                )
                .doOnSuccess(entry -> log.info("Score submitted: user={}, contest={}, rank={}",
                        request.getUserId(), contestId, entry.getRank()))
                .doOnError(e -> log.error("Score submission failed: {}", e.getMessage()));
    }

    public Flux<LeaderboardEntry> getLeaderboard(String contestId, int limit) {
        return redisLeaderboard.getTopEntries(contestId, limit);
    }

    public Mono<LeaderboardEntry> getUserRank(String contestId, String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found")))
                .flatMap(user -> redisLeaderboard.getUserRank(
                        contestId, userId, user.getUsername()
                ));
    }
}
