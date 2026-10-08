package com.project.qms.service;

import com.project.qms.dto.ActionProgressRequest;
import com.project.qms.dto.ActionVerificationRequest;
import com.project.qms.dto.CorrectiveActionRequest;
import com.project.qms.dto.CorrectiveActionResponse;
import com.project.qms.entity.CorrectiveAction;
import com.project.qms.entity.Defect;
import com.project.qms.entity.ProgressStatus;
import com.project.qms.entity.User;
import com.project.qms.entity.VerificationStatus;
import com.project.qms.exception.BusinessRuleException;
import com.project.qms.exception.ResourceNotFoundException;
import com.project.qms.repository.CorrectiveActionRepository;
import com.project.qms.repository.DefectRepository;
import com.project.qms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Corrective actions: assigning the work, following it up, and verifying that
 * it actually worked (FR-08).
 *
 * This service holds the two rules that make the project more than a CRUD
 * application:
 *
 *   FR-08.8  an action becomes OVERDUE the moment its target date passes while
 *            the work is unfinished - computed, never stored, so it can never
 *            be stale
 *
 *   FR-08.9  a completed action is verified EFFECTIVE or NOT_EFFECTIVE by an
 *            Inspector, and a NOT_EFFECTIVE verdict blocks the defect from
 *            progressing (FR-08.10)
 */
@Service
public class CorrectiveActionService {

    private final CorrectiveActionRepository actionRepository;
    private final DefectRepository defectRepository;
    private final UserRepository userRepository;

    public CorrectiveActionService(CorrectiveActionRepository actionRepository,
                                   DefectRepository defectRepository,
                                   UserRepository userRepository) {
        this.actionRepository = actionRepository;
        this.defectRepository = defectRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------
    //  Reading
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CorrectiveActionResponse> findAll() {
        return actionRepository.findAllByOrderByTargetDateAsc().stream()
                .map(CorrectiveActionService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CorrectiveActionResponse findById(Integer actionId) {
        return toResponse(getById(actionId));
    }

    @Transactional(readOnly = true)
    public List<CorrectiveActionResponse> findByDefect(Integer defectId) {
        return actionRepository.findByDefectDefectIdOrderByTargetDateAsc(defectId).stream()
                .map(CorrectiveActionService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CorrectiveActionResponse> findByResponsiblePerson(Integer userId) {
        return actionRepository.findByResponsiblePersonUserIdOrderByTargetDateAsc(userId).stream()
                .map(CorrectiveActionService::toResponse).toList();
    }

    /**
     * FR-08.8 - the overdue list. The same condition as check A4 in
     * database/03_verification_queries.sql, which is what it was verified
     * against, and covered by the index idx_actions_overdue.
     */
    @Transactional(readOnly = true)
    public List<CorrectiveActionResponse> findOverdue() {
        return actionRepository.findOverdue(LocalDate.now(), ProgressStatus.COMPLETED).stream()
                .map(CorrectiveActionService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CorrectiveAction getById(Integer actionId) {
        return actionRepository.findById(actionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Corrective action", actionId));
    }

    // ------------------------------------------------------------------
    //  Writing
    // ------------------------------------------------------------------

    /** FR-08.1 to FR-08.5. Assigns work to fix a defect. */
    @Transactional
    public CorrectiveActionResponse assign(Integer defectId, CorrectiveActionRequest request, User currentUser) {
        requireSupervisor(currentUser);
        Defect defect = defectRepository.findById(defectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Defect", defectId));

        if (defect.isClosed()) {
            throw new BusinessRuleException(
                    "That defect is CLOSED and can no longer take new corrective actions.");
        }

        if (request.actionDescription() == null || request.actionDescription().trim().length() < 10) {
            throw new BusinessRuleException("The action description must be at least 10 characters.");
        }

        // FR-08.4. @FutureOrPresent on the DTO already checks the common case;
        // this is the server-side backstop for a request that bypassed validation.
        if (request.targetDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleException(
                    "The target date must be today or a future date. Received: " + request.targetDate());
        }

        User responsible = userRepository.findById(request.responsiblePersonId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", request.responsiblePersonId()));

        if (!Boolean.TRUE.equals(responsible.getIsActive())) {
            throw new BusinessRuleException(
                    responsible.getFullName() + " is deactivated and cannot be given new work (FR-08.3).");
        }

        CorrectiveAction action = new CorrectiveAction();
        action.setDefect(defect);
        action.setResponsiblePerson(responsible);
        action.setActionDescription(request.actionDescription().trim());
        action.setTargetDate(request.targetDate());
        action.setProgressStatus(ProgressStatus.PENDING);
        action.setVerificationStatus(VerificationStatus.PENDING);
        action.setCreatedBy(currentUser);                            // FR-08.5

        return toResponse(actionRepository.saveAndFlush(action));
    }

    /**
     * FR-08.6 and FR-08.7: record progress, and mark the work finished.
     *
     * The completion date is set by the system, not typed by the user. If it
     * were an input field, two people would eventually record two different
     * answers for the same event.
     */
    @Transactional
    public CorrectiveActionResponse updateProgress(Integer actionId, ActionProgressRequest request,
                                                   User currentUser) {
        requireSupervisor(currentUser);
        CorrectiveAction action = getById(actionId);

        if (action.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new BusinessRuleException(
                    "This action has already been verified and can no longer be changed.");
        }

        ProgressStatus target = parseProgressStatus(request.progressStatus());

        action.setProgressStatus(target);
        if (request.progressRemark() != null) {
            action.setProgressRemark(request.progressRemark().trim());
        }

        if (target == ProgressStatus.COMPLETED) {
            action.setCompletionDate(LocalDate.now());               // FR-08.7
        } else {
            action.setCompletionDate(null);     // keeps the database CHECK happy
        }

        action.setUpdatedBy(currentUser);
        return toResponse(actionRepository.saveAndFlush(action));
    }

    /**
     * FR-08.9 to FR-08.11: the Inspector's verdict.
     *
     * This is the point that closes the loop. The work being finished is not
     * evidence that the problem is solved - someone has to check. If the
     * answer is NOT_EFFECTIVE, the message says what happens next, so the
     * supervisor knows a further action is needed (FR-08.10).
     */
    @Transactional
    public CorrectiveActionResponse verify(Integer actionId, ActionVerificationRequest request,
                                           User currentUser) {
        requireInspector(currentUser);
        CorrectiveAction action = getById(actionId);

        if (action.getProgressStatus() != ProgressStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "Only a COMPLETED corrective action can be verified. This one is "
                    + action.getProgressStatus().name() + ".");
        }

        VerificationStatus outcome = parseVerificationStatus(request.verificationStatus());

        action.setVerificationStatus(outcome);
        action.setVerificationRemark(request.verificationRemark().trim());
        action.setVerifiedBy(currentUser);                           // FR-08.11
        action.setVerifiedAt(LocalDateTime.now());                   // FR-08.11
        action.setUpdatedBy(currentUser);

        CorrectiveAction saved = actionRepository.saveAndFlush(action);

        return toResponse(saved);
    }

    /** The sentence the interface shows after a NOT_EFFECTIVE verdict (FR-08.10). */
    public String describeOutcome(VerificationStatus outcome, String defectRef) {
        if (outcome == VerificationStatus.NOT_EFFECTIVE) {
            return "Recorded as NOT_EFFECTIVE. " + defectRef
                 + " cannot be verified or closed until a further corrective action "
                 + "is completed and verified effective (FR-08.10).";
        }
        return "Recorded as EFFECTIVE. " + defectRef + " may now be verified.";
    }

    // ------------------------------------------------------------------

    /**
     * Phase 4 §6.2. Note the deliberate split, which is also Phase 3's point
     * about corrective and preventive action: the person who does the work and
     * the person who checks it are not the same person.
     *
     *   Supervisor assigns the work and updates its progress
     *   Inspector  verifies whether it actually worked
     */
    private static void requireSupervisor(User user) {
        if (!user.isSupervisor()) {
            throw new BusinessRuleException(
                    "Only a Production Supervisor may assign corrective actions or update their progress. "
                    + "You are signed in as " + user.getRole().name() + ".");
        }
    }

    private static void requireInspector(User user) {
        if (!user.isInspector()) {
            throw new BusinessRuleException(
                    "Only a Quality Inspector may verify whether a corrective action was effective. "
                    + "You are signed in as " + user.getRole().name() + ".");
        }
    }

    private static ProgressStatus parseProgressStatus(String text) {
        try {
            return ProgressStatus.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(
                    "Unknown progress status '" + text + "'. Valid values are: PENDING, IN_PROGRESS, COMPLETED.");
        }
    }

    private static VerificationStatus parseVerificationStatus(String text) {
        try {
            VerificationStatus value = VerificationStatus.valueOf(text.trim().toUpperCase());
            if (value == VerificationStatus.PENDING) {
                throw new BusinessRuleException(
                        "A verification outcome must be EFFECTIVE or NOT_EFFECTIVE - PENDING is where "
                        + "every action starts and is not a verdict.");
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(
                    "Unknown verification outcome '" + text + "'. Valid values are: EFFECTIVE, NOT_EFFECTIVE.");
        }
    }

    static CorrectiveActionResponse toResponse(CorrectiveAction a) {
        long daysOverdue = 0;
        if (a.isOverdue()) {
            daysOverdue = ChronoUnit.DAYS.between(a.getTargetDate(), LocalDate.now());
        }
        return new CorrectiveActionResponse(
                a.getActionId(),
                a.getDefect() == null ? null : a.getDefect().getDefectId(),
                a.getDefect() == null ? null : a.getDefect().getDefectRef(),
                a.getResponsiblePerson() == null ? null : a.getResponsiblePerson().getFullName(),
                a.getActionDescription(),
                a.getProgressRemark(),
                a.getTargetDate(),
                a.getCompletionDate(),
                a.getProgressStatus().name(),
                a.getVerificationStatus().name(),
                a.getVerificationRemark(),
                a.getVerifiedBy() == null ? null : a.getVerifiedBy().getFullName(),
                a.getVerifiedAt(),
                a.isOverdue(),
                daysOverdue,
                a.getCreatedBy() == null ? null : a.getCreatedBy().getFullName(),
                a.getCreatedAt());
    }
}
