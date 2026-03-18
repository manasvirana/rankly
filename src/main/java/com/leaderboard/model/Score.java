package com.leaderboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("scores")
public class Score {

    @Id
    private UUID id;
    private UUID userId;
    private UUID contestId;
    private Long score;
    private Instant submittedAt;
}
