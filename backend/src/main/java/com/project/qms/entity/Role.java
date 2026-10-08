package com.project.qms.entity;

/**
 * The access level of a user (FR-02.2).
 *
 * The values match the ENUM column 'users.role' exactly. The screens display
 * these as "Administrator", "Quality Inspector" and "Production Supervisor";
 * the stored value is the short machine name.
 */
public enum Role {
    ADMIN,
    INSPECTOR,
    SUPERVISOR
}
