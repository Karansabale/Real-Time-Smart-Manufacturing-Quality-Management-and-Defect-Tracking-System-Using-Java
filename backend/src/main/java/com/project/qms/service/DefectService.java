package com.project.qms.service;

import com.project.qms.dto.CorrectiveActionResponse;
import com.project.qms.dto.DefectDetailResponse;
import com.project.qms.dto.DefectHistoryResponse;
import com.project.qms.dto.DefectRequest;
import com.project.qms.dto.DefectResponse;
import com.project.qms.dto.DefectStatusRequest;
import com.project.qms.entity.CorrectiveAction;
import com.project.qms.entity.Defect;
import com.project.qms.entity.DefectStatus;
import com.project.qms.entity.DefectStatusHistory;
import com.project.qms.entity.Inspection;
import com.project.qms.entity.ProductionBatch;
import com.project.qms.entity.ProgressStatus;
import com.project.qms.entity.Severity;
import com.project.qms.entity.User;
import com.project.qms.exception.BusinessRuleException;
import com.project.qms.exception.ResourceNotFoundException;
import com.project.qms.repository.CorrectiveActionRepository;
import com.project.qms.repository.DefectRepository;
import com.project.qms.repository.DefectStatusHistoryRepository;
import com.project.qms.repository.InspectionRepository;
import com.project.qms.repository.ProductionBatchRepository;
import com.project.qms.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defect registration and tracking (FR-06, FR-07). The centre of the system.
 *
 * ============================================================================
 *  THE STATUS FLOW LIVES IN EXACTLY ONE METHOD - changeStatus() below.
 * ============================================================================
 *
 * This is Phase 5 decision C2, and it is the most important design decision in
 * the project. The rules about which status may follow which are not spread
 * across the controller, the interface and a stored procedure. They are in one
 * readable method, so there is one place to read them, one place to test them,
 * and one place to fix them.
 *
 * The two maps below are the whole rule set, taken directly from the
 * transition table in Phase 5 §7.4.
 */
@Service
public class DefectService {

    private final DefectRepository defectRepository;
    private final DefectStatusHistoryRepository historyRepository;
    private final CorrectiveActionRepository correctiveActionRepository;
    private final ProductRepository productRepository;
    private final ProductionBatchRepository batchRepository;
    private final InspectionRepository inspectionRepository;

    public DefectService(DefectRepository defectRepository,
                         DefectStatusHistoryRepository historyRepository,
                         CorrectiveActionRepository correctiveActionRepository,
                         ProductRepository productRepository,
                         ProductionBatchRepository batchRepository,
                         InspectionRepository inspectionRepository) {
        this.defectRepository = defectRepository;
        this.historyRepository = historyRepository;
        this.correctiveActionRepository = correctiveActionRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.inspectionRepository = inspectionRepository;
    }

    /**
     * WHICH MOVE IS ALLOWED FROM WHERE (Phase 5 §7.4).
     *
     *   OPEN                  -> UNDER_INVESTIGATION   (and nothing else: no skipping)
     *   UNDER_INVESTIGATION   -> CORRECTIVE_ACTION     (needs an action to exist)
     *                         -> OPEN                   (one step back, reason required)
     *   CORRECTIVE_ACTION     -> VERIFIED              (needs a completed, effective action)
     *                         -> UNDER_INVESTIGATION   (one step back, reason required)
     *   VERIFIED              -> CLOSED                (end of the road)
     *                         -> CORRECTIVE_ACTION     (verification failed, reason required)
     *   CLOSED                -> nothing               (terminal state)
     */
    private static final Map<DefectStatus, Set<DefectStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(Map.of(
                    DefectStatus.OPEN, EnumSet.of(DefectStatus.UNDER_INVESTIGATION),
                    DefectStatus.UNDER_INVESTIGATION, EnumSet.of(
                            DefectStatus.CORRECTIVE_ACTION, DefectStatus.OPEN),
                    DefectStatus.CORRECTIVE_ACTION, EnumSet.of(
                            DefectStatus.VERIFIED, DefectStatus.UNDER_INVESTIGATION),
                    DefectStatus.VERIFIED, EnumSet.of(
                            DefectStatus.CLOSED, DefectStatus.CORRECTIVE_ACTION),
                    DefectStatus.CLOSED, EnumSet.noneOf(DefectStatus.class)));

    // ==================================================================
    //  Reading
    // ==================================================================

    @Transactional(readOnly = true)
    public List<DefectResponse> findAll() {
        return defectRepository.findAllByOrderByCreatedAtDesc().stream().map(DefectService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DefectResponse> findByStatus(DefectStatus status) {
        return defectRepository.findByCurrentStatusOrderByCreatedAtDesc(status)
                .stream().map(DefectService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DefectResponse> findBySeverity(Severity severity) {
        return defectRepository.findBySeverityOrderByCreatedAtDesc(severity)
                .stream().map(DefectService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DefectResponse> findByProduct(Integer productId) {
        return defectRepository.findByProductProductIdOrderByCreatedAtDesc(productId)
                .stream().map(DefectService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Defect getById(Integer defectId) {
        return defectRepository.findById(defectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Defect", defectId));
    }

    @Transactional(readOnly = true)
    public DefectResponse findById(Integer defectId) {
        return toResponse(getById(defectId));
    }

    /** Lets the search box accept DEF-2026-0007 exactly as written on the shop floor. */
    @Transactional(readOnly = true)
    public DefectResponse findByRef(String defectRef) {
        Defect defect = defectRepository.findByDefectRef(defectRef.trim().toUpperCase())
                .orElseThrow(() -> ResourceNotFoundException.of("Defect", defectRef));
        return toResponse(defect);
    }

    /**
     * Everything the detail screen needs: the defect, its timeline, its
     * corrective actions, and which moves the person looking at it may make.
     */
    @Transactional(readOnly = true)
    public DefectDetailResponse findDetail(Integer defectId, User currentUser) {
        Defect defect = getById(defectId);

        List<DefectHistoryResponse> history =
                historyRepository.findByDefectDefectIdOrderByChangedAtAscHistoryIdAsc(defectId)
                        .stream().map(DefectService::toHistoryResponse).toList();

        List<CorrectiveActionResponse> actions =
                correctiveActionRepository.findByDefectDefectIdOrderByTargetDateAsc(defectId)
                        .stream().map(CorrectiveActionService::toResponse).toList();

        return new DefectDetailResponse(
                toResponse(defect),
                history,
                actions,
                allowedNextStatuses(defect.getCurrentStatus(), currentUser));
    }

    @Transactional(readOnly = true)
    public List<DefectHistoryResponse> findHistory(Integer defectId) {
        return historyRepository.findByDefectDefectIdOrderByChangedAtAscHistoryIdAsc(defectId)
                .stream().map(DefectService::toHistoryResponse).toList();
    }

    /** The work queue: live defects with nothing assigned yet. */
    @Transactional(readOnly = true)
    public List<DefectResponse> findWithoutCorrectiveAction() {
        return defectRepository.findDefectsWithoutCorrectiveAction()
                .stream().map(DefectService::toResponse).toList();
    }

    /**
     * The moves this user may make from this status. The interface uses it to
     * decide which buttons to show, so the screen can never offer a move the
     * server would refuse - the same map answers both questions.
     */
    public List<String> allowedNextStatuses(DefectStatus from, User user) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).stream()
                .filter(to -> isPermitted(from, to, user))
                .map(Enum::name)
                .sorted()
                .toList();
    }

    // ==================================================================
    //  Writing
    // ==================================================================

    /** FR-06. Registers a defect, always as OPEN, and opens its history. */
    @Transactional
    public DefectResponse registerDefect(DefectRequest request, User currentUser) {
        requireInspector(currentUser);
        Defect defect = new Defect();
        defect.setDefectRef(generateDefectRef());                   // FR-06.8
        defect.setProduct(productRepository.findById(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", request.productId())));

        if (request.batchId() != null) {
            ProductionBatch batch = batchRepository.findById(request.batchId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Production batch", request.batchId()));
            defect.setBatch(batch);
            // If the batch was given but no inspection, use the batch's product -
            // a defect can never be attributed to a product the batch does not make.
            if (!batch.getProduct().getProductId().equals(request.productId())) {
                throw new BusinessRuleException(
                        "Batch " + batch.getBatchNumber() + " is a run of "
                        + batch.getProduct().getProductCode() + ", not the selected product.");
            }
        }

        if (request.inspectionId() != null) {
            Inspection inspection = inspectionRepository.findById(request.inspectionId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Inspection", request.inspectionId()));
            defect.setInspection(inspection);
        }

        if (request.description() == null || request.description().trim().length() < 10) {
            throw new BusinessRuleException("The defect description must be at least 10 characters.");
        }

        defect.setReportedBy(currentUser);                          // FR-06.9
        defect.setDefectCategory(request.defectCategory());
        defect.setDescription(request.description().trim());
        defect.setSeverity(request.severity());
        defect.setUnitsAffected(request.unitsAffected());
        defect.setCurrentStatus(DefectStatus.OPEN);                 // FR-06.7
        defect.setCreatedBy(currentUser);

        Defect saved = defectRepository.saveAndFlush(defect);

        // The very first history row: from nothing, to OPEN.
        writeHistory(saved, null, DefectStatus.OPEN,
                "Defect registered", currentUser);

        return toResponse(saved);
    }

    /** FR-06.10: details may be edited only while the defect is still OPEN. */
    @Transactional
    public DefectResponse updateDefect(Integer defectId, DefectRequest request, User currentUser) {
        requireInspector(currentUser);
        Defect defect = getById(defectId);

        if (!defect.isEditable()) {
            throw new BusinessRuleException(
                    "This defect is " + defect.getCurrentStatus().name()
                    + " and can no longer be edited. Only defects still OPEN may be changed.");
        }

        if (request.description() == null || request.description().trim().length() < 10) {
            throw new BusinessRuleException("The defect description must be at least 10 characters.");
        }

        defect.setDefectCategory(request.defectCategory());
        defect.setDescription(request.description().trim());
        defect.setSeverity(request.severity());
        defect.setUnitsAffected(request.unitsAffected());
        if (request.inspectionId() != null) {
            defect.setInspection(inspectionRepository.findById(request.inspectionId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Inspection", request.inspectionId())));
        }
        defect.setUpdatedBy(currentUser);

        return toResponse(defectRepository.saveAndFlush(defect));
    }

    /**
     * ========================================================================
     *  THE STATUS FLOW. This is the method to read first.
     * ========================================================================
     *
     * FR-07.5  no skipping stages
     * FR-07.6  one step back is allowed, with a reason
     * FR-07.8  every change writes a history row
     * FR-08.14 a defect may not become VERIFIED without a completed action
     * FR-08.10 a NOT_EFFECTIVE verification must not let the defect progress
     *
     * The three checks run in order - is the move legal, is it this person's
     * move to make, and are the preconditions satisfied - and all three have
     * to pass before anything is written.
     *
     * The whole method is @Transactional, so the status update and its history
     * row commit together or not at all. Without that, a crash in between
     * would leave a defect that had moved with no record of having moved it,
     * which is exactly the kind of hole this project exists to prevent
     * (NFR-03.1, Phase 5 C5).
     */
    @Transactional
    public DefectResponse changeStatus(Integer defectId, DefectStatusRequest request, User currentUser) {
        Defect defect = getById(defectId);
        DefectStatus from = defect.getCurrentStatus();
        DefectStatus to = parseStatus(request.status());
        String remark = request.remark() == null ? null : request.remark().trim();

        // --- Check 1: is this move allowed at all? (FR-07.5) ---------------
        if (from == to) {
            throw new BusinessRuleException(
                    "This defect is already " + to.name() + ". Choose a different status.");
        }
        Set<DefectStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessRuleException(
                    "A defect cannot move from " + from.name() + " to " + to.name() + ". "
                    + describeAllowed(from) + " The defect lifecycle must be followed in order.");
        }

        // --- Check 2: is this person allowed to make it? (Phase 5 §7.4) ----
        if (!isPermitted(from, to, currentUser)) {
            throw new BusinessRuleException(
                    "Your role (" + currentUser.getRole().name() + ") cannot move a defect from "
                    + from.name() + " to " + to.name() + ".");
        }

        // --- Check 3: are the preconditions met? ---------------------------
        boolean reversal = to.ordinal() < from.ordinal();           // moves backwards in the lifecycle

        if (reversal && (remark == null || remark.isBlank())) {
            throw new BusinessRuleException(
                    "A reason is required when moving a defect back from " + from.name()
                    + " to " + to.name() + " (FR-07.6). Please explain why it is being reopened.");
        }

        if (from == DefectStatus.UNDER_INVESTIGATION && to == DefectStatus.CORRECTIVE_ACTION) {
            long actionCount = correctiveActionRepository
                    .findByDefectDefectIdOrderByTargetDateAsc(defectId).size();
            if (actionCount == 0) {
                throw new BusinessRuleException(
                        "At least one corrective action must be assigned before this defect can move "
                        + "to CORRECTIVE_ACTION. Add an action first.");
            }
        }

        if (from == DefectStatus.CORRECTIVE_ACTION && to == DefectStatus.VERIFIED) {
            List<CorrectiveAction> actions = correctiveActionRepository
                    .findByDefectDefectIdAndProgressStatus(defectId, ProgressStatus.COMPLETED);

            if (actions.isEmpty()) {
                throw new BusinessRuleException(
                        "This defect cannot be verified yet: no corrective action has been completed. "
                        + "Complete an action first (FR-08.14).");
            }
            boolean anyEffective = actions.stream().anyMatch(CorrectiveAction::isVerifiedEffective);
            if (!anyEffective) {
                throw new BusinessRuleException(
                        "The completed corrective action was verified NOT_EFFECTIVE, so this defect "
                        + "cannot be verified. A further corrective action is required (FR-08.10).");
            }
        }

        // --- All three checks passed. Write the change and its history. ----
        defect.setCurrentStatus(to);
        defect.setUpdatedBy(currentUser);
        Defect saved = defectRepository.saveAndFlush(defect);

        writeHistory(saved, from, to, remark, currentUser);         // FR-07.8

        return toResponse(saved);
    }

    /**
     * FR-06.14. A defect may only be deleted while it is still OPEN.
     * Once it has been investigated, deleting it would erase part of the
     * quality record - and its corrective actions and history refer to it.
     *
     * WHO: a Quality Inspector or an Administrator. Deleting is at least as
     * consequential as closing a defect, and those are the two roles Phase 4
     * §6.2 allows to close one. (The permission matrix has no row for deletion,
     * so this follows the closest rule that does exist.)
     *
     * THE HISTORY ROWS GO WITH IT. Every defect has a history row from the
     * moment it was registered, and the foreign key is RESTRICT, so a delete
     * that ignored them would fail - which is exactly what testing found:
     * FR-06.14 could never succeed for any defect at all. The rows that are
     * removed here are the record OF this defect, so removing them together
     * with the defect is coherent. Rows belonging to OTHER defects are never
     * touched, and a defect that already has corrective actions is refused
     * instead, because those actions are work records for other people.
     */
    @Transactional
    public void deleteDefect(Integer defectId, User currentUser) {
        requireInspectorOrAdmin(currentUser);
        Defect defect = getById(defectId);

        if (defect.getCurrentStatus() != DefectStatus.OPEN) {
            throw new BusinessRuleException(
                    "This defect is " + defect.getCurrentStatus().name()
                    + " and cannot be deleted. Only a defect still OPEN may be deleted (FR-06.14).");
        }

        if (!correctiveActionRepository.findByDefectDefectIdOrderByTargetDateAsc(defectId).isEmpty()) {
            throw new BusinessRuleException(
                    "This defect has corrective actions recorded against it. "
                    + "Those are work records for other people, so the defect cannot be deleted.");
        }

        historyRepository.deleteAll(
                historyRepository.findByDefectDefectIdOrderByChangedAtAscHistoryIdAsc(defectId));
        defectRepository.delete(defect);
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    /**
     * WHO may make which move (Phase 5 §7.4). Note that the answer depends on
     * both the starting point and the destination: moving INTO
     * CORRECTIVE_ACTION is a Supervisor's move, but moving BACK into it from
     * VERIFIED is an Inspector's, because it means the verification failed.
     */
    /**
     * Phase 4 §6.2: defects are registered and their details edited by a
     * Quality Inspector only. The Admin cannot register a defect - the Admin
     * administers the system, and defects are raised by the quality function.
     *
     * Note this is a different rule from the status flow, which several roles
     * take part in (see isPermitted below).
     */
    private static void requireInspector(User user) {
        if (!user.isInspector()) {
            throw new BusinessRuleException(
                    "Only a Quality Inspector may register or amend defect details. You are signed in as "
                    + user.getRole().name() + ".");
        }
    }

    /** Deleting a defect: the two roles that may also close one. */
    private static void requireInspectorOrAdmin(User user) {
        if (!user.isInspector() && !user.isAdmin()) {
            throw new BusinessRuleException(
                    "Only a Quality Inspector or an Administrator may delete a defect. You are signed in as "
                    + user.getRole().name() + ".");
        }
    }

    /**
     * The role table: may this person make this move? (Phase 4 §6.2)
     *
     * <p>It is deliberately package-private rather than private so that
     * {@code DefectLifecycleTest} can exercise the whole truth table in
     * milliseconds without a database or a Spring context. This is the only
     * line of production code that had to change to make the project testable
     * (Phase 12 §5).
     *
     * <p>Note for anyone reading it alone: this answers for the pairs the
     * lifecycle in {@code ALLOWED_TRANSITIONS} offers. For a pair it does not
     * offer the answer is not meaningful, and {@code changeStatus} never asks -
     * it refuses such a move against the lifecycle table first.
     */
    static boolean isPermitted(DefectStatus from, DefectStatus to, User user) {
        return switch (from) {
            case OPEN ->
                    to == DefectStatus.UNDER_INVESTIGATION && user.isSupervisor();
            case UNDER_INVESTIGATION ->
                    user.isSupervisor();
            case CORRECTIVE_ACTION ->
                    to == DefectStatus.VERIFIED ? user.isInspector() : user.isSupervisor();
            case VERIFIED ->
                    to == DefectStatus.CORRECTIVE_ACTION ? user.isInspector()
                                                         : (user.isInspector() || user.isAdmin());
            case CLOSED -> false;
        };
    }

    private static String describeAllowed(DefectStatus from) {
        Set<DefectStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (allowed.isEmpty()) {
            return "CLOSED is the end of the lifecycle; a closed defect cannot be reopened.";
        }
        java.util.List<String> names = allowed.stream().map(Enum::name).sorted().toList();
        String wording = names.size() == 1
                ? "the only possible next status is: "
                : "the possible next statuses are: ";
        return "From " + from.name() + " " + wording + String.join(" or ", names) + ".";
    }

    /**
     * Parses the status filter box of the defect register (FR-06.13).
     *
     * WHY IT LIVES HERE, PUBLIC AND STATIC. Two front doors take the same two
     * filter words: the register (DefectController) and the Defect Register
     * report (ReportService). One copy of the parsing means a nonsense filter
     * is refused in exactly the same words at both. The report used to skip
     * this step and simply matched nothing, so a mistyped filter produced a
     * perfectly normal-looking empty report - which reads as "there are no
     * such defects" and is the one answer a report must never give when it
     * means "that filter does not exist" (D-04, Phase 12).
     */
    public static DefectStatus parseStatusFilter(String text) {
        try {
            return DefectStatus.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown status filter '" + text + "'.");
        }
    }

    /** The severity twin of parseStatusFilter - see the note there. */
    public static Severity parseSeverityFilter(String text) {
        try {
            return Severity.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown severity filter '" + text + "'.");
        }
    }

    private static DefectStatus parseStatus(String statusText) {
        try {
            return DefectStatus.valueOf(statusText.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(
                    "Unknown status '" + statusText + "'. Valid values are: OPEN, UNDER_INVESTIGATION, "
                    + "CORRECTIVE_ACTION, VERIFIED, CLOSED.");
        }
    }

    /**
     * FR-06.8. Builds the next reference for this year, e.g. DEF-2026-0007.
     *
     * It looks for the HIGHEST existing reference rather than counting rows.
     * Counting would produce a duplicate as soon as a defect is deleted -
     * which is possible while it is still OPEN (FR-06.14).
     */
    private String generateDefectRef() {
        int year = LocalDate.now().getYear();
        String highest = defectRepository.findMaxDefectRefForYear(year);

        int next = 1;
        if (highest != null && highest.length() >= 4) {
            try {
                next = Integer.parseInt(highest.substring(highest.length() - 4)) + 1;
            } catch (NumberFormatException ex) {
                // A malformed reference should not stop a defect from being
                // registered. Fall back to the beginning and let the unique
                // constraint catch any clash.
                next = 1;
            }
        }
        return String.format("DEF-%d-%04d", year, next);
    }

    /** The one and only place a history row is created. Nothing ever updates or deletes one. */
    private void writeHistory(Defect defect, DefectStatus from, DefectStatus to,
                              String remark, User changedBy) {
        DefectStatusHistory history = new DefectStatusHistory();
        history.setDefect(defect);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setRemark(remark);
        history.setChangedBy(changedBy);
        history.setChangedAt(LocalDateTime.now());
        historyRepository.save(history);
    }

    static DefectResponse toResponse(Defect d) {
        return new DefectResponse(
                d.getDefectId(),
                d.getDefectRef(),
                d.getProduct() == null ? null : d.getProduct().getProductId(),
                d.getProduct() == null ? null : d.getProduct().getProductCode(),
                d.getProduct() == null ? null : d.getProduct().getProductName(),
                d.getBatch() == null ? null : d.getBatch().getBatchId(),
                d.getBatch() == null ? null : d.getBatch().getBatchNumber(),
                d.getInspection() == null ? null : d.getInspection().getInspectionId(),
                d.getReportedBy() == null ? null : d.getReportedBy().getFullName(),
                d.getDefectCategory().name(),
                d.getDescription(),
                d.getSeverity().name(),
                d.getUnitsAffected(),
                d.getCurrentStatus().name(),
                d.getAgeInDays(),
                d.getCreatedAt(),
                d.getCreatedBy() == null ? null : d.getCreatedBy().getFullName(),
                d.getUpdatedBy() == null ? null : d.getUpdatedBy().getFullName(),
                d.getUpdatedAt());
    }

    static DefectHistoryResponse toHistoryResponse(DefectStatusHistory h) {
        return new DefectHistoryResponse(
                h.getHistoryId(),
                h.getFromStatus() == null ? null : h.getFromStatus().name(),
                h.getToStatus().name(),
                h.getRemark(),
                h.getChangedBy() == null ? null : h.getChangedBy().getFullName(),
                h.getChangedAt());
    }
}
