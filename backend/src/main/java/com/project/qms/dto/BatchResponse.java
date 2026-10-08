package com.project.qms.dto;

import java.time.LocalDate;

/** A production batch as the screens see it, with its product already resolved. */
public record BatchResponse(
        Integer batchId,
        String batchNumber,
        Integer productId,
        String productCode,
        String productName,
        Integer quantityProduced,
        String productionLine,
        LocalDate startDate,
        LocalDate endDate,
        String productionStatus) {
}
