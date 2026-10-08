package com.project.qms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Change a batch's production status (FR-04.6).
 * Only PLANNED, IN_PROGRESS and COMPLETED are accepted; the service rejects
 * anything else with a message listing the valid values.
 */
public record BatchStatusRequest(
        @NotBlank(message = "Production status is required") String productionStatus) {
}
