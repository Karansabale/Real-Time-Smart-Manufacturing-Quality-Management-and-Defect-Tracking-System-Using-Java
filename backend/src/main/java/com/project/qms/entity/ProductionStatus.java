package com.project.qms.entity;

/**
 * The status of a production batch (FR-04.5, FR-04.6).
 * A new batch always starts as PLANNED. Only these three values exist.
 */
public enum ProductionStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED
}
