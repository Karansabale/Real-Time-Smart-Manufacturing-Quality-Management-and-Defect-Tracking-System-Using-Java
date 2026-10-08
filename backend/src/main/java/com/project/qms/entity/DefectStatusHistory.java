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
 * One row for every status change of a defect (FR-07.8). Maps to
 * 'defect_status_history'.
 *
 * THIS TABLE IS APPEND-ONLY. There is no update and no delete path anywhere
 * in the application, and the entity is written exactly once per status
 * change. That is the whole point: an audit trail that can be edited proves
 * nothing (Phase 5, D1).
 *
 * fromStatus is null on the very first row of each defect, meaning
 * "this defect was created". The database constraint ck_history_change makes
 * it impossible to record a change where nothing changed.
 */
@Entity
@Table(name = "defect_status_history")
public class DefectStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Integer historyId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "defect_id", nullable = false)
    private Defect defect;

    /** Null only on the first row of a defect: there was no previous status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 25)
    private DefectStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 25)
    private DefectStatus toStatus;

    /**
     * Why the change was made. MANDATORY when a defect is moved one step
     * backwards (FR-07.6) - enforced by DefectService, because only it knows
     * whether a move is a reversal.
     */
    @Column(name = "remark", length = 500)
    private String remark;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Read-only helper used when rendering the timeline. */
    public boolean isCreationRow() {
        return fromStatus == null;
    }

    public Integer getHistoryId() { return historyId; }
    public void setHistoryId(Integer historyId) { this.historyId = historyId; }

    public Defect getDefect() { return defect; }
    public void setDefect(Defect defect) { this.defect = defect; }

    public DefectStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(DefectStatus fromStatus) { this.fromStatus = fromStatus; }

    public DefectStatus getToStatus() { return toStatus; }
    public void setToStatus(DefectStatus toStatus) { this.toStatus = toStatus; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public User getChangedBy() { return changedBy; }
    public void setChangedBy(User changedBy) { this.changedBy = changedBy; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
