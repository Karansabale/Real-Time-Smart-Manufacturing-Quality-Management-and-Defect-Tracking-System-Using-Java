package com.project.qms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create or update a product (FR-03.1, FR-03.7). */
public record ProductRequest(
        @NotBlank(message = "Product code is required")
        @Size(min = 2, max = 20, message = "Product code must be 2 to 20 characters")
        String productCode,

        @NotBlank(message = "Product name is required")
        @Size(max = 100, message = "Product name must be at most 100 characters")
        String productName,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must be at most 50 characters")
        String category,

        @Size(max = 255, message = "Specification must be at most 255 characters")
        String specification) {
}
