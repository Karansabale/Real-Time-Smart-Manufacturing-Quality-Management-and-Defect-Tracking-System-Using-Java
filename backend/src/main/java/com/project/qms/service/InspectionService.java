package com.project.qms.service;

import com.project.qms.dto.InspectionRequest;
import com.project.qms.dto.InspectionResponse;
import com.project.qms.entity.Inspection;
import com.project.qms.entity.User;
import com.project.qms.exception.BusinessRuleException;
import com.project.qms.exception.ResourceNotFoundException;
import com.project.qms.repository.InspectionRepository;
import com.project.qms.repository.ProductionBatchRepository;
import com.project.qms.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Quality inspections (FR-05).
 *
 * Three rules from FR-05 are enforced here:
 *   FR-05.3  inspected quantity must be greater than zero
 *   FR-05.5  rejected quantity must not exceed inspected quantity
 *   FR-05.9  a PASS with rejected units must be confirmed explicitly
 *
 * FR-05.9 deserves a note. The warning is shown in the browser, but the
 * browser cannot be trusted - anyone can send a request straight to the API.
 * So the confirmation is sent back to the server and checked again here.
 * Validating only in the interface would mean the rule is decoration.
 */
@Service
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final ProductRepository productRepository;
    private final ProductionBatchRepository batchRepository;

    public InspectionService(InspectionRepository inspectionRepository,
                             ProductRepository productRepository,
                             ProductionBatchRepository batchRepository) {
        this.inspectionRepository = inspectionRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public List<InspectionResponse> findAll() {
        return inspectionRepository.findAllByOrderByInspectionDateDesc()
                .stream().map(InspectionService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InspectionResponse findById(Integer inspectionId) {
        return toResponse(getById(inspectionId));
    }

    @Transactional(readOnly = true)
    public Inspection getById(Integer inspectionId) {
        return inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inspection", inspectionId));
    }

    /** FR-05.13 - the inspection history of a product. */
    @Transactional(readOnly = true)
    public List<InspectionResponse> findByProduct(Integer productId) {
        return inspectionRepository.findByProductProductIdOrderByInspectionDateDesc(productId)
                .stream().map(InspectionService::toResponse).toList();
    }

    /** FR-05.13 - the inspection history of a batch. */
    @Transactional(readOnly = true)
    public List<InspectionResponse> findByBatch(Integer batchId) {
        return inspectionRepository.findByBatchBatchIdOrderByInspectionDateDesc(batchId)
                .stream().map(InspectionService::toResponse).toList();
    }

    /** FR-05.14 - filter by a date range. The end date is inclusive of the whole day. */
    @Transactional(readOnly = true)
    public List<InspectionResponse> findByDateRange(LocalDate from, LocalDate to) {
        LocalDateTime start = (from == null ? LocalDate.now().minusMonths(1) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now() : to).atTime(LocalTime.MAX);
        return inspectionRepository.findByInspectionDateBetweenOrderByInspectionDateDesc(start, end)
                .stream().map(InspectionService::toResponse).toList();
    }

    /**
     * Records a new inspection.
     *
     * The inspector is taken from the logged-in user, never from the request
     * body (FR-05.7). If the client could name the inspector, anyone could
     * file an inspection in someone else's name, and the record would be
     * worthless as evidence of who checked what.
     */
    @Transactional
    public InspectionResponse createInspection(InspectionRequest request, User currentUser) {
        requireInspector(currentUser);
        if (request.inspectedQty() == null || request.inspectedQty() <= 0) {
            throw new BusinessRuleException("Inspected quantity must be greater than zero.");
        }
        int rejected = request.rejectedQty() == null ? 0 : request.rejectedQty();
        if (rejected < 0) {
            throw new BusinessRuleException("Rejected quantity cannot be negative.");
        }
        if (rejected > request.inspectedQty()) {
            throw new BusinessRuleException(
                    "Rejected quantity (" + rejected + ") cannot be greater than the inspected quantity ("
                    + request.inspectedQty() + "). Please check the figures.");
        }

        boolean passWithRejects = request.result() != null
                && "PASS".equals(request.result().name())
                && rejected > 0;

        if (passWithRejects && !Boolean.TRUE.equals(request.confirmPassWithRejects())) {
            throw new BusinessRuleException(
                    "This inspection is recorded as PASS but has " + rejected
                    + " rejected unit(s). Please confirm this is correct before saving.");
        }

        Inspection inspection = new Inspection();
        inspection.setProduct(productRepository.findById(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", request.productId())));

        if (request.batchId() != null) {
            inspection.setBatch(batchRepository.findById(request.batchId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Production batch", request.batchId())));
        }

        inspection.setInspector(currentUser);                       // FR-05.7
        inspection.setInspectionDate(LocalDateTime.now());          // FR-05.7
        inspection.setInspectionType(request.inspectionType());
        inspection.setInspectedQty(request.inspectedQty());
        inspection.setRejectedQty(rejected);
        inspection.setResult(request.result());
        inspection.setRemarks(request.remarks() == null ? null : request.remarks().trim());
        inspection.setCreatedBy(currentUser);

        return toResponse(inspectionRepository.saveAndFlush(inspection));
    }

    /**
     * FR-05.12: only the remarks may be edited after saving. The quantities and
     * the result are frozen, because an inspection is a record of what was
     * actually measured at a moment in time. Letting someone change the
     * numbers afterwards would destroy its value.
     */
    @Transactional
    public InspectionResponse updateRemarks(Integer inspectionId, String remarks, User currentUser) {
        requireInspector(currentUser);
        Inspection inspection = getById(inspectionId);

        // FR-05.12 says "an inspection they created". Phase 4 §6.2 says the same:
        // Inspector, own records only. A different inspector editing someone
        // else's record would undermine the value of the signature on it.
        if (!inspection.getInspector().getUserId().equals(currentUser.getUserId())) {
            throw new BusinessRuleException(
                    "You can only edit remarks on inspections you performed. This one was recorded by "
                    + inspection.getInspector().getFullName() + ".");
        }

        inspection.setRemarks(remarks == null ? null : remarks.trim());
        inspection.setUpdatedBy(currentUser);
        return toResponse(inspectionRepository.saveAndFlush(inspection));
    }

    /**
     * Phase 4 §6.2: only a Quality Inspector creates inspections. The Admin
     * cannot, and neither can a Supervisor - the person who inspects must be
     * the quality function, not production.
     */
    private static void requireInspector(User user) {
        if (!user.isInspector()) {
            throw new BusinessRuleException(
                    "Only a Quality Inspector may record or amend inspections. You are signed in as "
                    + user.getRole().name() + ".");
        }
    }

    static InspectionResponse toResponse(Inspection i) {
        return new InspectionResponse(
                i.getInspectionId(),
                i.getProduct() == null ? null : i.getProduct().getProductId(),
                i.getProduct() == null ? null : i.getProduct().getProductCode(),
                i.getProduct() == null ? null : i.getProduct().getProductName(),
                i.getBatch() == null ? null : i.getBatch().getBatchId(),
                i.getBatch() == null ? null : i.getBatch().getBatchNumber(),
                i.getInspector() == null ? null : i.getInspector().getFullName(),
                i.getInspectionDate(),
                i.getInspectionType().name(),
                i.getInspectedQty(),
                i.getRejectedQty(),
                i.getAcceptedQty(),          // derived, never stored (Phase 5, D3)
                i.getResult().name(),
                i.getRemarks());
    }
}
