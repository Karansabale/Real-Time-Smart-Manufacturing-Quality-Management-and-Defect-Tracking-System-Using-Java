package com.project.qms.dto;

import jakarta.validation.constraints.NotBlank;

/** What the login form sends (FR-01.1). */
public record LoginRequest(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password) {
}
