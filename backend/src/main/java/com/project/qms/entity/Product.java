package com.project.qms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A part the factory makes. Maps to the 'products' table.
 *
 * A product cannot be deleted while production batches reference it
 * (FR-03.8). That rule is enforced by the database foreign key, and the
 * service asks the batch repository first so it can show a helpful message
 * instead of a database error.
 */
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Integer productId;

    /** Shop-floor identifier, e.g. P-101. Unique (FR-03.3). */
    @Column(name = "product_code", nullable = false, length = 20)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    /** Free-text grouping, e.g. "Machined Components". Not an ENUM (Phase 5, D9). */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** Optional technical specification (FR-03.4). */
    @Column(name = "specification", length = 255)
    private String specification;

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
}
