package com.project.qms.repository;

import com.project.qms.entity.Inspection;
import com.project.qms.entity.InspectionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for inspections (FR-05).
 *
 * The rejection-rate report is aggregated in ReportService rather than by a
 * grouped SQL query, because the volumes here are small and the Java approach
 * keeps the arithmetic in one readable place. The equivalent SQL is check A7
 * in database/03_verification_queries.sql, which is what the logic was
 * verified against.
 */
public interface InspectionRepository extends JpaRepository<Inspection, Integer> {

    List<Inspection> findAllByOrderByInspectionDateDesc();

    List<Inspection> findByProductProductIdOrderByInspectionDateDesc(Integer productId);

    List<Inspection> findByBatchBatchIdOrderByInspectionDateDesc(Integer batchId);

    /** Date-range filter (FR-05.14). */
    List<Inspection> findByInspectionDateBetweenOrderByInspectionDateDesc(LocalDateTime from, LocalDateTime to);

    List<Inspection> findByResultOrderByInspectionDateDesc(InspectionResult result);
}
