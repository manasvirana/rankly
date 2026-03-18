package com.leaderboard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class AuthRequests {

    @Data
    public static class Register {
        @NotBlank public String username;
        @NotBlank public String email;
        @NotBlank public String password;
    }

    @Data
    public static class Login {
        @NotBlank public String username;
        @NotBlank public String password;
    }
}