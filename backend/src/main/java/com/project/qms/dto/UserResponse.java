package com.project.qms.dto;

import java.time.LocalDateTime;

/**
 * A user as the screens see it.
 *
 * NOTE WHAT IS MISSING: passwordHash. The entity has it, the DTO does not,
 * so it can never be serialised into a response by accident (Phase 5, C3).
 */
public record UserResponse(
        Integer userId,
        String username,
        String fullName,
        String role,
        Boolean isActive,
        LocalDateTime createdAt) {
}
