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

import java.time.LocalDateTime;

/**
 * One quality check. Maps to 'inspections'.
 *
 * NOTE WHAT IS MISSING: there is no acceptedQuantity field. Accepted quantity
 * is always inspected minus rejected, so it is derived by getAcceptedQty()
 * below (Phase 5, D3). Storing it would let the three numbers contradict each
 * other.
 */
@Entity
@Table(name = "inspections")
public class Inspection extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Integer inspectionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Optional: the specific batch inspected (FR-05.1). May be null. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private ProductionBatch batch;

    /** The logged-in inspector (FR-05.7). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inspector_id", nullable = false)
    private User inspector;

    @Column(name = "inspection_date", nullable = false)
    private LocalDateTime inspectionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", nullable = false, length = 15)
    private InspectionType inspectionType;

    @Column(name = "inspected_qty", nullable = false)
    private Integer inspectedQty;

    @Column(name = "rejected_qty", nullable = false)
    private Integer rejectedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private InspectionResult result;

    @Column(name = "remarks", length = 500)
    private String remarks;

    /**
     * Derived, never stored (Phase 5, D3). The database guarantees
     * rejected <= inspected, so this can never be negative.
     */
    public int getAcceptedQty() {
        int inspected = inspectedQty == null ? 0 : inspectedQty;
        int rejected = rejectedQty == null ? 0 : rejectedQty;
        return inspected - rejected;
    }

    /** Read-only helper used by the service to spot the PASS-with-rejects case (FR-05.9). */
    public boolean isPassWithRejects() {
        return result == InspectionResult.PASS && rejectedQty != null && rejectedQty > 0;
    }

    /** Read-only helper for the rejection-rate report (FR-10.5). */
    public double getRejectionPercentage() {
        int inspected = inspectedQty == null ? 0 : inspectedQty;
        if (inspected == 0) {
            return 0.0;
        }
        return 100.0 * (rejectedQty == null ? 0 : rejectedQty) / inspected;
    }

    public Integer getInspectionId() { return inspectionId; }
    public void setInspectionId(Integer inspectionId) { this.inspectionId = inspectionId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public ProductionBatch getBatch() { return batch; }
    public void setBatch(ProductionBatch batch) { this.batch = batch; }

    public User getInspector() { return inspector; }
    public void setInspector(User inspector) { this.inspector = inspector; }

    public LocalDateTime getInspectionDate() { return inspectionDate; }
    public void setInspectionDate(LocalDateTime inspectionDate) { this.inspectionDate = inspectionDate; }

    public InspectionType getInspectionType() { return inspectionType; }
    public void setInspectionType(InspectionType inspectionType) { this.inspectionType = inspectionType; }

    public Integer getInspectedQty() { return inspectedQty; }
    public void setInspectedQty(Integer inspectedQty) { this.inspectedQty = inspectedQty; }

    public Integer getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(Integer rejectedQty) { this.rejectedQty = rejectedQty; }

    public InspectionResult getResult() { return result; }
    public void setResult(InspectionResult result) { this.result = result; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
