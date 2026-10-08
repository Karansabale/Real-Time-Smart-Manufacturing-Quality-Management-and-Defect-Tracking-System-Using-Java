package com.project.qms.repository;

import com.project.qms.entity.ProductionBatch;
import com.project.qms.entity.ProductionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Data access for production batches (FR-04). */
public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Integer> {

    Optional<ProductionBatch> findByBatchNumber(String batchNumber);

    boolean existsByBatchNumber(String batchNumber);

    List<ProductionBatch> findAllByOrderByBatchNumberDesc();

    /** Batches of one product - also the "is anything using this product?" check (FR-03.8). */
    List<ProductionBatch> findByProductProductIdOrderByBatchNumberDesc(Integer productId);

    /**
     * Answers FR-03.8 before the delete is attempted: "does any batch reference
     * this product?" The database would also refuse, but asking first lets the
     * service return a helpful message instead of a raw constraint error.
     */
    boolean existsByProductProductId(Integer productId);

    List<ProductionBatch> findByProductionStatus(ProductionStatus productionStatus);

    long countByProductionStatus(ProductionStatus productionStatus);
}
