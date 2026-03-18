package com.leaderboard.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaderboard.service.LeaderboardBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderboardWebSocketHandler implements WebSocketHandler {

    private final LeaderboardBroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        String contestId = path.substring(path.lastIndexOf('/') + 1);

        log.info("WebSocket connected: sessionId={}, contestId={}", session.getId(), contestId);

        return session.send(
                broadcastService.subscribe(contestId)
                        .map(update -> {
                            try {
                                String json = objectMapper.writeValueAsString(update);
                                return session.textMessage(json);
                            } catch (Exception e) {
                                log.error("Failed to serialize leaderboard update", e);
                                return session.textMessage("{}");
                            }
                        })
        ).doFinally(signal ->
                log.info("WebSocket disconnected: sessionId={}, contestId={}, signal={}",
                        session.getId(), contestId, signal)
        );
    }
}
