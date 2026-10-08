package com.project.qms.service;

import com.project.qms.dto.BatchRequest;
import com.project.qms.dto.BatchResponse;
import com.project.qms.entity.ProductionBatch;
import com.project.qms.entity.ProductionStatus;
import com.project.qms.entity.User;
import com.project.qms.exception.BusinessRuleException;
import com.project.qms.exception.DuplicateResourceException;
import com.project.qms.exception.ResourceNotFoundException;
import com.project.qms.repository.ProductionBatchRepository;
import com.project.qms.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Production batches (FR-04).
 *
 * Two rules from FR-04 live here:
 *   - a new batch always starts as PLANNED (FR-04.5)
 *   - the status may only be one of the three known values (FR-04.6); an
 *     unknown value produces a message listing the valid ones rather than a
 *     confusing enum conversion error
 */
@Service
public class BatchService {

    private final ProductionBatchRepository batchRepository;
    private final ProductRepository productRepository;

    public BatchService(ProductionBatchRepository batchRepository, ProductRepository productRepository) {
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findAll() {
        return batchRepository.findAllByOrderByBatchNumberDesc().stream().map(BatchService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findByProduct(Integer productId) {
        return batchRepository.findByProductProductIdOrderByBatchNumberDesc(productId)
                .stream().map(BatchService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductionBatch getById(Integer batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Production batch", batchId));
    }

    @Transactional(readOnly = true)
    public BatchResponse findById(Integer batchId) {
        return toResponse(getById(batchId));
    }

    @Transactional
    public BatchResponse createBatch(BatchRequest request, User currentUser) {
        requireSupervisor(currentUser);
        if (batchRepository.existsByBatchNumber(request.batchNumber().trim())) {
            throw new DuplicateResourceException(
                    "Batch number '" + request.batchNumber() + "' already exists.");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException("The end date cannot be before the start date.");
        }

        ProductionBatch batch = new ProductionBatch();
        batch.setProduct(productRepository.findById(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", request.productId())));
        batch.setBatchNumber(request.batchNumber().trim());
        batch.setQuantityProduced(request.quantityProduced());
        batch.setProductionLine(request.productionLine().trim());
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setProductionStatus(ProductionStatus.PLANNED);   // FR-04.5
        batch.setCreatedBy(currentUser);

        return toResponse(batchRepository.saveAndFlush(batch));
    }

    /**
     * FR-04.6 and FR-04.7.
     *
     * Completing a batch records the end date automatically if it was not
     * given, because FR-04.7 says the end date is captured when the status
     * becomes COMPLETED. Asking the user to type it twice would be a good way
     * to end up with two different answers.
     */
    @Transactional
    public BatchResponse updateStatus(Integer batchId, String statusText, User currentUser) {
        requireSupervisor(currentUser);
        ProductionBatch batch = getById(batchId);
        ProductionStatus target = parseStatus(statusText);

        batch.setProductionStatus(target);
        if (target == ProductionStatus.COMPLETED && batch.getEndDate() == null) {
            batch.setEndDate(LocalDate.now());
        }
        batch.setUpdatedBy(currentUser);

        return toResponse(batchRepository.saveAndFlush(batch));
    }

    /**
     * Phase 4 §6.2: only a Production Supervisor may create a batch or change
     * its production status. Everyone may view batches.
     */
    private static void requireSupervisor(User user) {
        if (!user.isSupervisor()) {
            throw new BusinessRuleException(
                    "Only a Production Supervisor may create or update production batches. "
                    + "You are signed in as " + user.getRole().name() + ".");
        }
    }

    /** Turns a string into a status, or explains what the valid values are. */
    private static ProductionStatus parseStatus(String statusText) {
        try {
            return ProductionStatus.valueOf(statusText.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(
                    "Unknown production status '" + statusText + "'. Valid values are: PLANNED, IN_PROGRESS, COMPLETED.");
        }
    }

    static BatchResponse toResponse(ProductionBatch batch) {
        return new BatchResponse(
                batch.getBatchId(),
                batch.getBatchNumber(),
                batch.getProduct() == null ? null : batch.getProduct().getProductId(),
                batch.getProduct() == null ? null : batch.getProduct().getProductCode(),
                batch.getProduct() == null ? null : batch.getProduct().getProductName(),
                batch.getQuantityProduced(),
                batch.getProductionLine(),
                batch.getStartDate(),
                batch.getEndDate(),
                batch.getProductionStatus().name());
    }
}
