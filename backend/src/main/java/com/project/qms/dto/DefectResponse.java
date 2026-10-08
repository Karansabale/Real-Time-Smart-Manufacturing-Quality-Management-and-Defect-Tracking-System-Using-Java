package com.project.qms.dto;

import java.time.LocalDateTime;

/** A defect row as the list screen and reports see it. */
public record DefectResponse(
        Integer defectId,
        String defectRef,
        Integer productId,
        String productCode,
        String productName,
        Integer batchId,
        String batchNumber,
        Integer inspectionId,
        String reportedBy,
        String defectCategory,
        String description,
        String severity,
        Integer unitsAffected,
        String currentStatus,
        long ageInDays,
        LocalDateTime createdAt,
        String createdBy,
        String updatedBy,
        LocalDateTime updatedAt) {
}
