package com.project.qms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Move a defect to a new status (FR-07).
 *
 * 'remark' is optional for a normal forward move but MANDATORY when moving
 * backwards one step (FR-07.6). The service knows which is which, so the rule
 * lives there and the message it produces says exactly what is missing.
 */
public record DefectStatusRequest(
        @NotBlank(message = "Target status is required") String status,

        @Size(max = 500, message = "Remark must be at most 500 characters")
        String remark) {
}
