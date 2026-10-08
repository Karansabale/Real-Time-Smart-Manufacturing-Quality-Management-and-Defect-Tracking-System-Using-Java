package com.project.qms.dto;

import java.time.LocalDateTime;

/** A product as the screens see it. */
public record ProductResponse(
        Integer productId,
        String productCode,
        String productName,
        String category,
        String specification,
        String createdBy,
        LocalDateTime createdAt) {
}
