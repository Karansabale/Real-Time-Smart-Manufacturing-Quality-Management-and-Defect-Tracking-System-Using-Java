package com.project.qms.repository;

import com.project.qms.entity.DefectStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the append-only status history (FR-07.8).
 *
 * Note what is NOT here: there is no method that updates or deletes a history
 * row, and there never will be. The service may only save new ones. That is
 * what makes the trail trustworthy (Phase 5, D1).
 */
public interface DefectStatusHistoryRepository extends JpaRepository<DefectStatusHistory, Integer> {

    /** The full story of one defect, oldest first - the timeline on screen 8 (FR-06.12). */
    List<DefectStatusHistory> findByDefectDefectIdOrderByChangedAtAscHistoryIdAsc(Integer defectId);

    /**
     * The most recent row for a defect. Used to prove that the defect's
     * current_status agrees with its own history - the same check as A14 in
     * the verification queries.
     */
    Optional<DefectStatusHistory> findTopByDefectDefectIdOrderByChangedAtDescHistoryIdDesc(Integer defectId);
}
