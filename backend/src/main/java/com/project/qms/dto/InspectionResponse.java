package com.project.qms.dto;

import java.time.LocalDateTime;

/** An inspection as the screens see it, with acceptedQty already derived. */
public record InspectionResponse(
        Integer inspectionId,
        Integer productId,
        String productCode,
        String productName,
        Integer batchId,
        String batchNumber,
        String inspector,
        LocalDateTime inspectionDate,
        String inspectionType,
        Integer inspectedQty,
        Integer rejectedQty,
        Integer acceptedQty,
        String result,
        String remarks) {
}
