package com.project.qms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Update how far the work has got (FR-08.6, FR-08.7).
 *
 * Setting progressStatus to COMPLETED is what completes the action, and the
 * service then records the completion date today. The two are inseparable -
 * the database has a CHECK constraint requiring completion_date to be present
 * exactly when the status is COMPLETED.
 */
public record ActionProgressRequest(
        @NotBlank(message = "Progress status is required") String progressStatus,

        @Size(max = 500, message = "Progress remark must be at most 500 characters")
        String progressRemark) {
}
