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
import java.time.LocalDateTime;

/**
 * Work assigned to fix a defect, plus its verification. Maps to
 * 'corrective_actions'.
 *
 * TWO SEPARATE STATUS FIELDS, and the difference is the heart of the project:
 *
 *   progressStatus     - "is the work done?"          (the Supervisor's view)
 *   verificationStatus - "did the fix actually work?" (the Inspector's view)
 *
 * A completed action can be verified NOT_EFFECTIVE. When that happens the
 * defect must not move forward (FR-08.10). The sample data contains exactly
 * that story on DEF-2026-0003.
 *
 * There is NO overdue column. "Overdue" changes every midnight, so it is
 * derived by isOverdue() below (FR-08.8) - a stored flag would be wrong the
 * next morning.
 */
@Entity
@Table(name = "corrective_actions")
public class CorrectiveAction extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id")
    private Integer actionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "defect_id", nullable = false)
    private Defect defect;

    /** Who must do the work (FR-08.3). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "responsible_person_id", nullable = false)
    private User responsiblePerson;

    /** At least 10 characters (FR-08.2). */
    @Column(name = "action_description", nullable = false, length = 500)
    private String actionDescription;

    @Column(name = "progress_remark", length = 500)
    private String progressRemark;

    /**
     * The deadline (FR-08.4). Must be today or later at creation time - a rule
     * that lives in the service, because a CHECK constraint cannot depend on
     * today's date.
     */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", nullable = false, length = 15)
    private ProgressStatus progressStatus = ProgressStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verification_remark", length = 500)
    private String verificationRemark;

    /** Null until an Inspector verifies (Phase 5, D6). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * FR-08.8, derived exactly as the SQL does it:
     * target date has passed AND the work is not finished.
     */
    public boolean isOverdue() {
        return targetDate != null
                && targetDate.isBefore(LocalDate.now())
                && progressStatus != ProgressStatus.COMPLETED;
    }

    /** Read-only helper: has this action been verified as having worked? */
    public boolean isVerifiedEffective() {
        return verificationStatus == VerificationStatus.EFFECTIVE;
    }

    /** Read-only helper: is the work finished? */
    public boolean isCompleted() {
        return progressStatus == ProgressStatus.COMPLETED;
    }

    /** Read-only helper: done and confirmed to have worked (the FR-08.14 gate). */
    public boolean isCompletedAndEffective() {
        return isCompleted() && isVerifiedEffective();
    }

    public Integer getActionId() { return actionId; }
    public void setActionId(Integer actionId) { this.actionId = actionId; }

    public Defect getDefect() { return defect; }
    public void setDefect(Defect defect) { this.defect = defect; }

    public User getResponsiblePerson() { return responsiblePerson; }
    public void setResponsiblePerson(User responsiblePerson) { this.responsiblePerson = responsiblePerson; }

    public String getActionDescription() { return actionDescription; }
    public void setActionDescription(String actionDescription) { this.actionDescription = actionDescription; }

    public String getProgressRemark() { return progressRemark; }
    public void setProgressRemark(String progressRemark) { this.progressRemark = progressRemark; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public LocalDate getCompletionDate() { return completionDate; }
    public void setCompletionDate(LocalDate completionDate) { this.completionDate = completionDate; }

    public ProgressStatus getProgressStatus() { return progressStatus; }
    public void setProgressStatus(ProgressStatus progressStatus) { this.progressStatus = progressStatus; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getVerificationRemark() { return verificationRemark; }
    public void setVerificationRemark(String verificationRemark) { this.verificationRemark = verificationRemark; }

    public User getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(User verifiedBy) { this.verifiedBy = verifiedBy; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
}
