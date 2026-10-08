package com.project.qms.entity;

/**
 * The outcome recorded by an Inspector after checking a completed corrective
 * action (FR-08.9). Starts as PENDING and can only be set to EFFECTIVE or
 * NOT_EFFECTIVE once the action has been completed.
 */
public enum VerificationStatus {
    PENDING,
    EFFECTIVE,
    NOT_EFFECTIVE
}
