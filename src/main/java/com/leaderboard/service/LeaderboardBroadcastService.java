package com.leaderboard.service;

import com.leaderboard.dto.LeaderboardUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LeaderboardBroadcastService {

    private final ConcurrentHashMap<String, Sinks.Many<LeaderboardUpdate>> contestSinks
            = new ConcurrentHashMap<>();

    private Sinks.Many<LeaderboardUpdate> getSink(String contestId) {
        return contestSinks.computeIfAbsent(contestId, id ->
                Sinks.many().multicast().onBackpressureBuffer(256));
    }

    public void broadcast(String contestId, LeaderboardUpdate update) {
        Sinks.Many<LeaderboardUpdate> sink = getSink(contestId);
        Sinks.EmitResult result = sink.tryEmitNext(update);

        if (result.isFailure()) {
            log.warn("Broadcast failed for contest {}: {}", contestId, result);
        } else {
            log.debug("Broadcasted leaderboard update to contest {}", contestId);
        }
    }

    public Flux<LeaderboardUpdate> subscribe(String contestId) {
        log.info("New WebSocket subscriber for contest {}", contestId);
        return getSink(contestId).asFlux();
    }

    public void closeSink(String contestId) {
        Sinks.Many<LeaderboardUpdate> sink = contestSinks.remove(contestId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("Closed broadcast sink for contest {}", contestId);
        }
    }

    public int getSubscriberCount(String contestId) {
        Sinks.Many<LeaderboardUpdate> sink = contestSinks.get(contestId);
        return sink != null ? sink.currentSubscriberCount() : 0;
    }
}
