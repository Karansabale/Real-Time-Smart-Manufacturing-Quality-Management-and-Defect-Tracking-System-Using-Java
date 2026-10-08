package com.project.qms.entity;

/**
 * The five stages of the defect lifecycle (FR-07).
 *
 * OPEN -> UNDER_INVESTIGATION -> CORRECTIVE_ACTION -> VERIFIED -> CLOSED
 *
 * The allowed movements between these values - and the fact that some of them
 * may only be reversed with a reason - are enforced in
 * {@link com.project.qms.service.DefectService#changeStatus}. The order of the
 * constants here is the normal forward order and is used by the dashboard.
 */
public enum DefectStatus {
    OPEN,
    UNDER_INVESTIGATION,
    CORRECTIVE_ACTION,
    VERIFIED,
    CLOSED
}
