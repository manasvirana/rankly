package com.leaderboard.repository;

import com.leaderboard.model.Contest;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ContestRepository extends R2dbcRepository<Contest, UUID> {

    Flux<Contest> findByStatus(String status);

    @Query("UPDATE contests SET status = 'ENDED' WHERE id = :id RETURNING *")
    Mono<Contest> endContest(UUID id);
}
