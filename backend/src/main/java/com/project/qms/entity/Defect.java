package com.project.qms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A quality problem found. Maps to 'defects'. The centre of the whole system.
 *
 * 'current_status' holds only the present position of the defect. How it got
 * there - every move, who made it and why - lives in DefectStatusHistory,
 * which is what makes the "no skipping stages" rule (FR-07.5) auditable.
 *
 * The rules for which status may follow which are NOT here. They live in
 * DefectService.changeStatus(), in one place, where they can be tested
 * (Phase 5, C2). This class only holds the data and a couple of read-only
 * helpers.
 */
@Entity
@Table(name = "defects")
public class Defect extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "defect_id")
    private Integer defectId;

    /** Human-readable reference, e.g. DEF-2026-0007 (FR-06.8). Unique. */
    @Column(name = "defect_ref", nullable = false, length = 20)
    private String defectRef;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Optional: the batch this defect came from, when known (Phase 5, D5). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private ProductionBatch batch;

    /** Optional: set when the defect was found during an inspection (FR-06.2). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inspection_id")
    private Inspection inspection;

    /** The inspector who raised it (FR-06.9). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "defect_category", nullable = false, length = 20)
    private DefectCategory defectCategory;

    /** At least 10 characters - checked by the DTO before it reaches here (FR-06.4). */
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "units_affected", nullable = false)
    private Integer unitsAffected = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 25)
    private DefectStatus currentStatus = DefectStatus.OPEN;

    /** Read-only helper: is this defect finished? */
    public boolean isClosed() {
        return currentStatus == DefectStatus.CLOSED;
    }

    /**
     * Read-only helper: how many days has this defect been on the books?
     * Used by the ageing report (FR-07.9). Derived, never stored - a stored
     * age would be wrong by one every midnight.
     */
    public long getAgeInDays() {
        if (getCreatedAt() == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(getCreatedAt().toLocalDate(), LocalDate.now());
    }

    /** Read-only helper: may an inspector still edit the details? (FR-06.10) */
    public boolean isEditable() {
        return currentStatus == DefectStatus.OPEN;
    }

    public Integer getDefectId() { return defectId; }
    public void setDefectId(Integer defectId) { this.defectId = defectId; }

    public String getDefectRef() { return defectRef; }
    public void setDefectRef(String defectRef) { this.defectRef = defectRef; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public ProductionBatch getBatch() { return batch; }
    public void setBatch(ProductionBatch batch) { this.batch = batch; }

    public Inspection getInspection() { return inspection; }
    public void setInspection(Inspection inspection) { this.inspection = inspection; }

    public User getReportedBy() { return reportedBy; }
    public void setReportedBy(User reportedBy) { this.reportedBy = reportedBy; }

    public DefectCategory getDefectCategory() { return defectCategory; }
    public void setDefectCategory(DefectCategory defectCategory) { this.defectCategory = defectCategory; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Integer getUnitsAffected() { return unitsAffected; }
    public void setUnitsAffected(Integer unitsAffected) { this.unitsAffected = unitsAffected; }

    public DefectStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(DefectStatus currentStatus) { this.currentStatus = currentStatus; }
}
