package com.leaderboard.service;

import com.leaderboard.dto.LeaderboardEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLeaderboardService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${leaderboard.cache-ttl-hours:48}")
    private int cacheTtlHours;

    @Value("${leaderboard.max-entries:100}")
    private int maxEntries;

    // "v2": entries use the packed format below, which keys without it don't.
    private static final String KEY_PREFIX = "leaderboard:v2:";

    // A Redis score is a double, which holds whole numbers exactly up to 2^53.
    // The score needs 30 bits (max 1,000,000,000), leaving 23 bits for the time bonus.
    private static final long TIME_SLOTS = 1L << 23; // 8,388,608 seconds, about 97 days

    // Adds the member, or updates it only if the new value is bigger, then refreshes the expiry.
    // Redis runs a script as a single atomic step. Returns 1 if the leaderboard changed.
    private static final RedisScript<Long> SAVE_IF_BETTER = RedisScript.of("""
            local changed = redis.call('ZADD', KEYS[1], 'GT', 'CH', ARGV[1], ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return changed
            """, Long.class);

    /**
     * Packs score and time into one whole number: a higher score always ranks first,
     * and for equal scores the earlier submission ranks first.
     */
    static long encode(long score, Instant submittedAt, Instant contestStart) {
        long elapsed = Duration.between(contestStart, submittedAt).getSeconds();
        long clamped = Math.max(0, Math.min(elapsed, TIME_SLOTS - 1));
        return score * TIME_SLOTS + (TIME_SLOTS - 1 - clamped);
    }

    static long decodeScore(double encoded) {
        return (long) encoded / TIME_SLOTS;
    }

    public Mono<Boolean> updateScore(String contestId, String userId, String username,
                                     long rawScore, Instant submittedAt, Instant contestStart) {
        String key = KEY_PREFIX + contestId;
        String member = userId + ":" + username;
        String encoded = Long.toString(encode(rawScore, submittedAt, contestStart));
        String ttlSeconds = Long.toString(Duration.ofHours(cacheTtlHours).toSeconds());

        return redisTemplate.execute(SAVE_IF_BETTER, List.of(key), List.of(encoded, member, ttlSeconds))
                .next()
                .map(changed -> changed > 0);
    }

    public Flux<LeaderboardEntry> getTopEntries(String contestId, int limit) {
        String key = KEY_PREFIX + contestId;
        int actualLimit = Math.min(limit, maxEntries);

        return redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, org.springframework.data.domain.Range.closed(0L, (long) actualLimit - 1))
                .index()
                .map(indexedEntry -> {
                    String member = indexedEntry.getT2().getValue();
                    long rawScore = decodeScore(indexedEntry.getT2().getScore());

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
            long rawScore = decodeScore(tuple.getT2());

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
