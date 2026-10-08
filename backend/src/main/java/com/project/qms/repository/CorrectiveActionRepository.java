package com.project.qms.repository;

import com.project.qms.entity.CorrectiveAction;
import com.project.qms.entity.ProgressStatus;
import com.project.qms.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Data access for corrective actions (FR-08). */
public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Integer> {

    List<CorrectiveAction> findAllByOrderByTargetDateAsc();

    /** All actions raised against one defect - the panel on the defect detail screen. */
    List<CorrectiveAction> findByDefectDefectIdOrderByTargetDateAsc(Integer defectId);

    /** Every action for a defect that is already finished - the FR-08.14 check. */
    List<CorrectiveAction> findByDefectDefectIdAndProgressStatus(Integer defectId, ProgressStatus progressStatus);

    List<CorrectiveAction> findByResponsiblePersonUserIdOrderByTargetDateAsc(Integer userId);

    /**
     * The overdue list (FR-08.8) - the query the whole project was built for.
     *
     * The condition is exactly the one used in check A4 of the verification
     * queries: target date has passed AND the work is not COMPLETED. The index
     * idx_actions_overdue covers this query.
     */
    @Query("""
           SELECT ca FROM CorrectiveAction ca
           WHERE ca.targetDate < :today
             AND ca.progressStatus <> :completed
           ORDER BY ca.targetDate ASC
           """)
    List<CorrectiveAction> findOverdue(@Param("today") LocalDate today,
                                       @Param("completed") ProgressStatus completed);

    long countByVerificationStatus(VerificationStatus verificationStatus);

    List<CorrectiveAction> findByVerificationStatus(VerificationStatus verificationStatus);
}
