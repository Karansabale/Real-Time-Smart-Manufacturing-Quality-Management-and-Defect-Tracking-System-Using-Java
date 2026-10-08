package com.project.qms.repository;

import com.project.qms.entity.Defect;
import com.project.qms.entity.DefectStatus;
import com.project.qms.entity.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Data access for defects (FR-06, FR-07). */
public interface DefectRepository extends JpaRepository<Defect, Integer> {

    Optional<Defect> findByDefectRef(String defectRef);

    List<Defect> findAllByOrderByCreatedAtDesc();

    List<Defect> findByCurrentStatusOrderByCreatedAtDesc(DefectStatus currentStatus);

    List<Defect> findBySeverityOrderByCreatedAtDesc(Severity severity);

    List<Defect> findByProductProductIdOrderByCreatedAtDesc(Integer productId);

    List<Defect> findByBatchBatchIdOrderByCreatedAtDesc(Integer batchId);

    /** Dashboard counts - one per status tile (FR-09.2 to FR-09.6). */
    long countByCurrentStatus(DefectStatus currentStatus);

    List<Defect> findTop5ByOrderByCreatedAtDesc();

    /**
     * The defect reference generator (FR-06.8).
     *
     * Finds the highest reference already issued this year, e.g. DEF-2026-0007.
     * DefectService takes the last four characters, adds one, and pads back to
     * four digits. Doing it by maximum rather than by counting rows means a
     * deleted or skipped record can never cause a duplicate reference.
     */
    @Query("SELECT MAX(d.defectRef) FROM Defect d WHERE d.defectRef LIKE CONCAT('DEF-', :year, '-%')")
    String findMaxDefectRefForYear(@Param("year") int year);

    /**
     * The work queue: defects that are still live but have no corrective action
     * assigned yet. JPQL subquery - the same logic as check A13 in the
     * verification queries.
     */
    @Query("""
           SELECT d FROM Defect d
           WHERE d.currentStatus <> com.project.qms.entity.DefectStatus.CLOSED
             AND NOT EXISTS (SELECT ca FROM CorrectiveAction ca WHERE ca.defect = d)
           ORDER BY d.createdAt ASC
           """)
    List<Defect> findDefectsWithoutCorrectiveAction();
}
