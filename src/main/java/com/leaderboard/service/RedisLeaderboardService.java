package com.leaderboard.service;

import com.leaderboard.dto.LeaderboardEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLeaderboardService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${leaderboard.cache-ttl-hours:48}")
    private int cacheTtlHours;

    @Value("${leaderboard.max-entries:100}")
    private int maxEntries;

    private static final String KEY_PREFIX = "leaderboard:";

    public Mono<Boolean> updateScore(String contestId, String userId, String username, long rawScore) {
        String key = KEY_PREFIX + contestId;
        String member = userId + ":" + username;

        double encodedScore = rawScore + (1.0 / (Instant.now().toEpochMilli() % 1000000));

        ReactiveZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();

        return zsetOps.score(key, member)
                .defaultIfEmpty(Double.NEGATIVE_INFINITY)
                .flatMap(existingEncoded -> {
                    double existingRawScore = existingEncoded == Double.NEGATIVE_INFINITY
                            ? Double.NEGATIVE_INFINITY
                            : Math.floor(existingEncoded);

                    if (rawScore <= existingRawScore) {
                        log.debug("Score {} not higher than existing {} for user {}", rawScore, existingRawScore, userId);
                        return Mono.just(false);
                    }

                    return zsetOps.add(key, member, encodedScore)
                            .then(redisTemplate.expire(key, Duration.ofHours(cacheTtlHours)))
                            .thenReturn(true);
                });
    }

    public Flux<LeaderboardEntry> getTopEntries(String contestId, int limit) {
        String key = KEY_PREFIX + contestId;
        int actualLimit = Math.min(limit, maxEntries);

        return redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, org.springframework.data.domain.Range.closed(0L, (long) actualLimit - 1))
                .index()
                .map(indexedEntry -> {
                    String member = indexedEntry.getT2().getValue();
                    double encodedScore = indexedEntry.getT2().getScore();

                    long rawScore = (long) encodedScore;

                    String[] parts = member.split(":", 2);
                    String userId = parts[0];
                    String username = parts.length > 1 ? parts[1] : "unknown";

                    return LeaderboardEntry.builder()
                            .rank(indexedEntry.getT1() + 1)
                            .userId(userId)
                            .username(username)
                            .score(rawScore)
                            .build();
                });
    }

    public Mono<LeaderboardEntry> getUserRank(String contestId, String userId, String username) {
        String key = KEY_PREFIX + contestId;
        String member = userId + ":" + username;

        ReactiveZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();

        return Mono.zip(
                zsetOps.reverseRank(key, member).defaultIfEmpty(-1L),
                zsetOps.score(key, member).defaultIfEmpty(0.0)
        ).map(tuple -> {
            long rank = tuple.getT1();
            double encodedScore = tuple.getT2();
            long rawScore = (long) encodedScore;

            return LeaderboardEntry.builder()
                    .rank(rank == -1 ? -1 : rank + 1)
                    .userId(userId)
                    .username(username)
                    .score(rawScore)
                    .build();
        });
    }

    public Mono<Boolean> deleteLeaderboard(String contestId) {
        return redisTemplate.delete(KEY_PREFIX + contestId)
                .map(count -> count > 0);
    }
}
