package com.leaderboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class Requests {

    @Data
    public static class CreateUser {
        @NotBlank public String username;
        @NotBlank public String email;
    }

    @Data
    public static class CreateContest {
        @NotBlank public String name;
        public String description;
        @NotNull public Instant startsAt;
        @NotNull public Instant endsAt;
    }

    @Data
    public static class SubmitScore {
        @NotNull public String userId;
        @NotNull public Long score;
    }
}
