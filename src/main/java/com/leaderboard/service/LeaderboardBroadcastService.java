package com.leaderboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaderboard.dto.LeaderboardUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardBroadcastService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Sinks.Many<LeaderboardUpdate>> localSinks
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
     * Subscribe to Redis channel for a contest.
     * Returns a Flux that emits updates as they arrive from Redis.
     * This works across ALL instances — true horizontal scaling.
     */
    public Flux<LeaderboardUpdate> subscribe(String contestId) {
        log.info("New WebSocket subscriber for contest {}", contestId);

        return redisTemplate.listenToChannel(CHANNEL_PREFIX + contestId)
                .mapNotNull(message -> {
                    try {
                        return objectMapper.readValue(
                                message.getMessage(),
                                LeaderboardUpdate.class
                        );
                    } catch (Exception e) {
                        log.error("Failed to deserialize update", e);
                        return null;
                    }
                });
    }

    public void closeSink(String contestId) {
        Sinks.Many<LeaderboardUpdate> sink = localSinks.remove(contestId);
        if (sink != null) sink.tryEmitComplete();
    }
}