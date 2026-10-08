package com.project.qms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;

/**
 * Supplies the four audit columns that five of the seven tables share
 * (FR-13): who created the record, when, who last changed it, and when.
 *
 * WHY A SUPERCLASS: products, production_batches, inspections, defects and
 * corrective_actions all need exactly these four columns. Writing them out
 * five times would be five copies of the same thing to keep in step, and
 * FR-13.5 requires them to be filled in automatically rather than typed by
 * the user - which is what the two lifecycle methods below do.
 *
 * NOTE: this class is NOT a table. @MappedSuperclass means "this is not an
 * entity of its own; its fields become columns of whoever extends it". The
 * seven tables still map to exactly seven entity classes.
 *
 * The 'users' table is deliberately not audited this way: a record of who
 * created a user would be odd on the very first user, and the schema only
 * gives users their own created_at / updated_at.
 */
@MappedSuperclass
public abstract class AuditableEntity {

    /**
     * EAGER on purpose. Every response DTO shows the creator's name, and with
     * spring.jpa.open-in-view=false a LAZY association can only be read inside
     * a transaction. EAGER removes a whole class of LazyInitializationException
     * for no real cost at this data volume.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Runs automatically just before the first INSERT (FR-13.5). */
    @PrePersist
    protected void stampCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /** Runs automatically before every UPDATE (FR-13.5). */
    @PreUpdate
    protected void stampUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public User getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(User updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
