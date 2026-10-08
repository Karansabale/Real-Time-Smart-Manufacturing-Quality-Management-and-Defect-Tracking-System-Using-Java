package com.project.qms.dto;

import com.project.qms.entity.InspectionResult;
import com.project.qms.entity.InspectionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Record an inspection (FR-05). */
public record InspectionRequest(
        @NotNull(message = "Product is required") Integer productId,

        /** Optional - an inspection need not name a batch (FR-05.1). */
        Integer batchId,

        @NotNull(message = "Inspection type is required") InspectionType inspectionType,

        @NotNull(message = "Inspected quantity is required")
        @Min(value = 1, message = "Inspected quantity must be greater than zero")
        Integer inspectedQty,

        @NotNull(message = "Rejected quantity is required")
        @Min(value = 0, message = "Rejected quantity cannot be negative")
        Integer rejectedQty,

        @NotNull(message = "Result is required") InspectionResult result,

        @Size(max = 500, message = "Remarks must be at most 500 characters")
        String remarks,

        /**
         * FR-05.9. When the result is PASS but units were rejected, the
         * interface warns the inspector and asks for confirmation. This flag
         * carries that confirmation to the server, which refuses to save
         * without it rather than trusting the browser.
         */
        Boolean confirmPassWithRejects) {
}
