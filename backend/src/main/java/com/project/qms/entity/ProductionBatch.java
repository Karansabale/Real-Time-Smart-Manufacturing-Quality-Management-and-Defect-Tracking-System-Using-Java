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

/**
 * One manufacturing run of one product. Maps to 'production_batches'.
 *
 * 'product' is a real object reference rather than an integer, so Java code
 * can write batch.getProduct().getProductCode() and never has to look up the
 * product itself (Phase 5, entity rule 2). Hibernate handles the join.
 */
@Entity
@Table(name = "production_batches")
public class ProductionBatch extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Integer batchId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Unique business reference, e.g. BATCH-2026-001. */
    @Column(name = "batch_number", nullable = false, length = 30)
    private String batchNumber;

    @Column(name = "quantity_produced", nullable = false)
    private Integer quantityProduced;

    /** Stored as text, not a master table (Phase 5, D9). */
    @Column(name = "production_line", nullable = false, length = 30)
    private String productionLine;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Filled in when the batch reaches COMPLETED (FR-04.7). */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "production_status", nullable = false, length = 15)
    private ProductionStatus productionStatus = ProductionStatus.PLANNED;

    /** Read-only helper: has this batch finished? Not a business rule. */
    public boolean isCompleted() {
        return productionStatus == ProductionStatus.COMPLETED;
    }

    public Integer getBatchId() { return batchId; }
    public void setBatchId(Integer batchId) { this.batchId = batchId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public Integer getQuantityProduced() { return quantityProduced; }
    public void setQuantityProduced(Integer quantityProduced) { this.quantityProduced = quantityProduced; }

    public String getProductionLine() { return productionLine; }
    public void setProductionLine(String productionLine) { this.productionLine = productionLine; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public ProductionStatus getProductionStatus() { return productionStatus; }
    public void setProductionStatus(ProductionStatus productionStatus) { this.productionStatus = productionStatus; }
}
