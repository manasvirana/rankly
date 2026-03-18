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
@Table("contests")
public class Contest {

    @Id
    private UUID id;
    private String name;
    private String description;
    private String status;
    private Instant startsAt;
    private Instant endsAt;
    private Instant createdAt;
}
