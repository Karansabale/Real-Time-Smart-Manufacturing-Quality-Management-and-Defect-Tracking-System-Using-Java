package com.project.qms.entity;

/**
 * How far the work on a corrective action has got (FR-08.6, FR-08.7).
 * This answers "is the work done?" - not "did the fix work?", which is
 * {@link VerificationStatus}.
 */
public enum ProgressStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}
