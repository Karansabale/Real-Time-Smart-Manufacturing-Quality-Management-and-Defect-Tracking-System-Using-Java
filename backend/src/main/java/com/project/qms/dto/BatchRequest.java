package com.project.qms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Create a production batch (FR-04.1). */
public record BatchRequest(
        @NotNull(message = "Product is required") Integer productId,

        @NotBlank(message = "Batch number is required")
        @Size(max = 30, message = "Batch number must be at most 30 characters")
        String batchNumber,

        @NotNull(message = "Quantity produced is required")
        @Min(value = 1, message = "Quantity produced must be greater than zero")
        Integer quantityProduced,

        @NotBlank(message = "Production line is required")
        @Size(max = 30, message = "Production line must be at most 30 characters")
        String productionLine,

        @NotNull(message = "Start date is required") LocalDate startDate,

        LocalDate endDate) {
}
