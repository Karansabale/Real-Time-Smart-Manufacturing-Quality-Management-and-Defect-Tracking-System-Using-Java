package com.project.qms.dto;

import java.time.LocalDateTime;

/** One row of the status timeline on the defect detail screen (FR-06.12). */
public record DefectHistoryResponse(
        Integer historyId,
        String fromStatus,
        String toStatus,
        String remark,
        String changedBy,
        LocalDateTime changedAt) {
}
