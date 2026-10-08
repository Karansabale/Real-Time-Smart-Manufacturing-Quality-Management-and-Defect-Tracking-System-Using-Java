package com.project.qms.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The single error shape every failure returns (FR-12.8).
 * Because one class builds every error, the frontend needs one error handler
 * and can never be handed a Java stack trace (FR-12.9).
 */
public record ApiError(
        int status,
        String error,
        String message,
        List<String> details,
        LocalDateTime timestamp) {
}
