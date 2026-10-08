package com.project.qms.service;

import com.project.qms.dto.CountRow;
import com.project.qms.dto.DashboardSummaryResponse;
import com.project.qms.entity.Defect;
import com.project.qms.entity.DefectCategory;
import com.project.qms.entity.DefectStatus;
import com.project.qms.entity.Severity;
import com.project.qms.repository.CorrectiveActionRepository;
import com.project.qms.repository.DefectRepository;
import com.project.qms.repository.ProductRepository;
import com.project.qms.repository.ProductionBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The dashboard numbers (FR-09).
 *
 * EVERY NUMBER IS COMPUTED LIVE FROM THE DATABASE ON EVERY CALL (FR-09.12).
 * Nothing is cached. A dashboard that showed figures from five minutes ago
 * would be worse than useless in a quality meeting - someone would make a
 * decision on stale numbers and never know.
 *
 * One method returns everything, so the screen makes one request and paints
 * once instead of firing six calls and flickering as each one lands.
 */
@Service
public class DashboardService {

    private final DefectRepository defectRepository;
    private final CorrectiveActionRepository actionRepository;
    private final ProductRepository productRepository;
    private final ProductionBatchRepository batchRepository;

    public DashboardService(DefectRepository defectRepository,
                            CorrectiveActionRepository actionRepository,
                            ProductRepository productRepository,
                            ProductionBatchRepository batchRepository) {
        this.defectRepository = defectRepository;
        this.actionRepository = actionRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        // The five status tiles use the indexed count queries (FR-09.2 - FR-09.6).
        long open = defectRepository.countByCurrentStatus(DefectStatus.OPEN);
        long underInvestigation = defectRepository.countByCurrentStatus(DefectStatus.UNDER_INVESTIGATION);
        long correctiveAction = defectRepository.countByCurrentStatus(DefectStatus.CORRECTIVE_ACTION);
        long verified = defectRepository.countByCurrentStatus(DefectStatus.VERIFIED);
        long closed = defectRepository.countByCurrentStatus(DefectStatus.CLOSED);

        // The two breakdowns need the defects themselves, so they are loaded
        // once and grouped in Java. At this volume that is one query instead of
        // eleven, and the grouping stays readable.
        List<Defect> allDefects = defectRepository.findAll();

        return new DashboardSummaryResponse(
                allDefects.size(),
                open,
                underInvestigation,
                correctiveAction,
                verified,
                closed,
                actionRepository.findOverdue(java.time.LocalDate.now(),
                        com.project.qms.entity.ProgressStatus.COMPLETED).size(),   // FR-09.7
                productRepository.count(),                                          // FR-09.1
                batchRepository.count(),
                breakdownBySeverity(allDefects),
                breakdownByCategory(allDefects),
                defectRepository.findTop5ByOrderByCreatedAtDesc().stream()
                        .map(DefectService::toResponse).toList());                  // FR-09.11
    }

    private static List<CountRow> breakdownBySeverity(List<Defect> defects) {
        Map<Severity, Long> counts = defects.stream()
                .collect(Collectors.groupingBy(Defect::getSeverity, Collectors.counting()));

        // Listed most serious first, so the chart reads like a priority list.
        List<CountRow> rows = new ArrayList<>();
        for (Severity severity : List.of(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW)) {
            rows.add(new CountRow(severity.name(), counts.getOrDefault(severity, 0L)));
        }
        return rows;
    }

    private static List<CountRow> breakdownByCategory(List<Defect> defects) {
        Map<DefectCategory, Long> counts = defects.stream()
                .collect(Collectors.groupingBy(Defect::getDefectCategory, Collectors.counting()));

        List<CountRow> rows = new ArrayList<>();
        for (DefectCategory category : DefectCategory.values()) {
            long count = counts.getOrDefault(category, 0L);
            if (count > 0) {
                rows.add(new CountRow(category.name(), count));
            }
        }
        rows.sort((a, b) -> Long.compare(b.count(), a.count()));
        return rows;
    }
}
