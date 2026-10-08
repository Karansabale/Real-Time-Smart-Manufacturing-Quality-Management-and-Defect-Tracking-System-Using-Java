package com.project.qms.dto;

import com.project.qms.entity.DefectCategory;
import com.project.qms.entity.Severity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Register a defect (FR-06.1 to FR-06.6), or edit one while it is still OPEN
 * (FR-06.10).
 */
public record DefectRequest(
        @NotNull(message = "Product is required") Integer productId,

        /** Optional: the batch the defect came from, when known (FR-06.1). */
        Integer batchId,

        /** Optional: the inspection that found it (FR-06.2). */
        Integer inspectionId,

        @NotNull(message = "Defect category is required") DefectCategory defectCategory,

        @Size(min = 10, max = 500, message = "Description must be 10 to 500 characters")
        String description,

        @NotNull(message = "Severity is required") Severity severity,

        @NotNull(message = "Units affected is required")
        @Min(value = 1, message = "Units affected must be greater than zero")
        Integer unitsAffected) {
}
