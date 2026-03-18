package com.leaderboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardUpdate {
    private String contestId;
    private List<LeaderboardEntry> topEntries;
    private Instant updatedAt;
    private String eventType;
}
