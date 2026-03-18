package com.leaderboard.repository;

import com.leaderboard.model.Score;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ScoreRepository extends R2dbcRepository<Score, UUID> {

    Mono<Score> findByUserIdAndContestId(UUID userId, UUID contestId);

    @Query("""
        INSERT INTO scores (id, user_id, contest_id, score, submitted_at)
        VALUES (gen_random_uuid(), :userId, :contestId, :score, NOW())
        ON CONFLICT (user_id, contest_id)
        DO UPDATE SET
            score = CASE WHEN scores.score < EXCLUDED.score 
                         THEN EXCLUDED.score 
                         ELSE scores.score END,
            submitted_at = CASE WHEN scores.score < EXCLUDED.score 
                                THEN NOW() 
                                ELSE scores.submitted_at END
    """)
    Mono<Void> upsertScore(UUID userId, UUID contestId, Long score);
}