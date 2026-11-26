package com.leaderboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaderboard.dto.LeaderboardUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardBroadcastService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // One shared Redis subscription per contest on this instance, however many viewers it has.
    private final ConcurrentHashMap<String, Flux<LeaderboardUpdate>> sharedStreams
            = new ConcurrentHashMap<>();

    private static final String CHANNEL_PREFIX = "rankly:contest:";

    public void broadcast(String contestId, LeaderboardUpdate update) {
        try {
            String json = objectMapper.writeValueAsString(update);
            redisTemplate.convertAndSend(CHANNEL_PREFIX + contestId, json)
                    .subscribe(
                            count -> log.debug("Published to {} subscribers", count),
                            err -> log.error("Redis publish failed", err)
                    );
        } catch (Exception e) {
            log.error("Failed to serialize leaderboard update", e);
        }
    }

    /**
     * Updates for a contest, published by any instance through Redis.
     * All viewers of a contest on this instance share one Redis subscription.
     */
    public Flux<LeaderboardUpdate> subscribe(String contestId) {
        log.info("New WebSocket subscriber for contest {}", contestId);

        return sharedStreams
                .computeIfAbsent(contestId, this::listen)
                // A slow viewer skips to the newest leaderboard instead of delaying the others.
                .onBackpressureLatest();
    }

    // Opens with the first viewer and closes after the last one leaves.
    private Flux<LeaderboardUpdate> listen(String contestId) {
        return redisTemplate.listenToChannel(CHANNEL_PREFIX + contestId)
                .mapNotNull(message -> deserialize(message.getMessage()))
                .doOnSubscribe(s -> log.info("Opened Redis subscription for contest {}", contestId))
                .doFinally(signal -> {
                    sharedStreams.remove(contestId);
                    log.info("Closed Redis subscription for contest {}", contestId);
                })
                .publish()
                .refCount(1);
    }

    private LeaderboardUpdate deserialize(String json) {
        try {
            return objectMapper.readValue(json, LeaderboardUpdate.class);
        } catch (Exception e) {
            log.error("Failed to deserialize update", e);
            return null;
        }
    }
}