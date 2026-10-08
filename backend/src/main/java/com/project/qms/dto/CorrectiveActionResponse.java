package com.project.qms.dto;

import java.time.LocalDateTime;

/**
 * A corrective action as the screens see it.
 *
 * 'overdue' and 'daysOverdue' are computed, never stored (FR-08.8). The
 * boolean is what makes the row print red; the number is what makes the
 * screen useful ("4 days overdue" is actionable, a red dot is not).
 */
public record CorrectiveActionResponse(
        Integer actionId,
        Integer defectId,
        String defectRef,
        String responsiblePerson,
        String actionDescription,
        String progressRemark,
        java.time.LocalDate targetDate,
        java.time.LocalDate completionDate,
        String progressStatus,
        String verificationStatus,
        String verificationRemark,
        String verifiedBy,
        LocalDateTime verifiedAt,
        boolean overdue,
        long daysOverdue,
        String createdBy,
        LocalDateTime createdAt) {
}
