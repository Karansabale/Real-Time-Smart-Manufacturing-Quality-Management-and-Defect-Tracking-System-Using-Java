package com.project.qms.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Assign a corrective action to a defect (FR-08.1 to FR-08.4).
 *
 * @FutureOrPresent implements FR-08.4 ("target date must be today or a future
 * date") declaratively. It cannot be a database CHECK constraint, because a
 * CHECK must be true forever and "today or later" is not - a row that was
 * valid yesterday would become invalid today (Phase 7 §7.3).
 */
public record CorrectiveActionRequest(
        @NotNull(message = "Responsible person is required") Integer responsiblePersonId,

        @Size(min = 10, max = 500, message = "Action description must be 10 to 500 characters")
        String actionDescription,

        @NotNull(message = "Target date is required")
        @FutureOrPresent(message = "Target date must be today or a future date")
        LocalDate targetDate) {
}
