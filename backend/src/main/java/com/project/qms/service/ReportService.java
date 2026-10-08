package com.project.qms.service;

import com.project.qms.dto.CorrectiveActionResponse;
import com.project.qms.dto.DefectResponse;
import com.project.qms.dto.ReportResponse;
import com.project.qms.dto.ReportRow;
import com.project.qms.entity.DefectStatus;
import com.project.qms.entity.Inspection;
import com.project.qms.entity.Severity;
import com.project.qms.entity.ProductionBatch;
import com.project.qms.repository.CorrectiveActionRepository;
import com.project.qms.repository.InspectionRepository;
import com.project.qms.repository.ProductionBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The four reports (FR-10).
 *
 * All four return the same shape - columns plus rows - so the report screen
 * has one table renderer instead of four. Two requirements are handled here
 * rather than being left to the interface:
 *
 *   FR-10.11  a report that finds nothing returns a MESSAGE, not an empty
 *             table. "No defects matched the filters you chose" is an answer;
 *             a blank grid looks like a broken screen.
 *
 *   FR-10.12  every percentage passes through safePercentage() below, so a
 *             division by zero can never produce NaN or infinity. In manufacturing
 *             data this is not hypothetical: a brand-new product has no
 *             inspections at all, and a product with zero inspections has an
 *             undefined rejection rate, not a zero one.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final InspectionRepository inspectionRepository;
    private final ProductionBatchRepository batchRepository;
    private final CorrectiveActionRepository actionRepository;
    private final DefectService defectService;

    public ReportService(InspectionRepository inspectionRepository,
                         ProductionBatchRepository batchRepository,
                         CorrectiveActionRepository actionRepository,
                         DefectService defectService) {
        this.inspectionRepository = inspectionRepository;
        this.batchRepository = batchRepository;
        this.actionRepository = actionRepository;
        this.defectService = defectService;
    }

    // ------------------------------------------------------------------
    //  FR-10.12 - the guard
    // ------------------------------------------------------------------

    /**
     * Percentage that cannot blow up.
     *
     * Returns 0.0 when the denominator is zero, and rounds to two decimals for
     * display. Every percentage in this class comes through here, so there is
     * exactly one place where the divide-by-zero question is answered.
     */
    public static double safePercentage(long part, long whole) {
        if (whole == 0) {
            return 0.0;                     // undefined, shown as 0.00 rather than NaN
        }
        return Math.round((100.0 * part / whole) * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    //  Report 1 - defect register (FR-10.1, FR-10.2)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReportResponse defectReport(String status, String severity, Integer productId) {
        List<String> columns = List.of("Reference", "Product", "Category", "Severity",
                "Units", "Status", "Raised on", "Age (days)");

        // Parsed, not compared as text: an unknown filter is refused with the
        // register's own wording instead of quietly matching nothing (D-04).
        DefectStatus statusFilter =
                (status == null || status.isBlank()) ? null : DefectService.parseStatusFilter(status);
        Severity severityFilter =
                (severity == null || severity.isBlank()) ? null : DefectService.parseSeverityFilter(severity);

        List<DefectResponse> defects = defectService.findAll();
        List<ReportRow> rows = new ArrayList<>();

        for (DefectResponse d : defects) {
            if (statusFilter != null && !statusFilter.name().equals(d.currentStatus())) {
                continue;
            }
            if (severityFilter != null && !severityFilter.name().equals(d.severity())) {
                continue;
            }
            if (productId != null && !productId.equals(d.productId())) {
                continue;
            }
            rows.add(new ReportRow(List.of(
                    d.defectRef(),
                    d.productCode() + " - " + d.productName(),
                    d.defectCategory(),
                    d.severity(),
                    String.valueOf(d.unitsAffected()),
                    d.currentStatus(),
                    d.createdAt() == null ? "" : d.createdAt().format(DATE),
                    String.valueOf(d.ageInDays()))));
        }

        return build("Defect Register", columns, rows,
                "No defects matched the filters you chose. Try widening the date range "
                + "or clearing the status and severity filters.");
    }

    // ------------------------------------------------------------------
    //  Report 2 - rejection rate per product (FR-10.5, FR-10.6)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReportResponse inspectionReport(LocalDate from, LocalDate to) {
        List<String> columns = List.of("Product code", "Product name", "Inspections",
                "Inspected", "Rejected", "Accepted", "Rejection rate %");

        Map<String, long[]> totals = new TreeMap<>();   // product code -> [inspections, inspected, rejected]
        Map<String, String> names = new LinkedHashMap<>();

        for (Inspection inspection : inspectionRepository.findAllByOrderByInspectionDateDesc()) {
            if (from != null && inspection.getInspectionDate().toLocalDate().isBefore(from)) {
                continue;
            }
            if (to != null && inspection.getInspectionDate().toLocalDate().isAfter(to)) {
                continue;
            }
            String code = inspection.getProduct().getProductCode();
            names.put(code, inspection.getProduct().getProductName());
            long[] row = totals.computeIfAbsent(code, k -> new long[3]);
            row[0] += 1;
            row[1] += inspection.getInspectedQty();
            row[2] += inspection.getRejectedQty();
        }

        List<ReportRow> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            long[] t = entry.getValue();
            long accepted = t[1] - t[2];
            rows.add(new ReportRow(List.of(
                    entry.getKey(),
                    names.get(entry.getKey()),
                    String.valueOf(t[0]),
                    String.valueOf(t[1]),
                    String.valueOf(t[2]),
                    String.valueOf(accepted),
                    String.format("%.2f", safePercentage(t[2], t[1])))));
        }
        rows.sort(Comparator.comparing(
                row -> row.cells().get(6), Comparator.reverseOrder()));

        return build("Rejection Rate by Product", columns, rows,
                "No inspections were recorded in the selected period, so there is no "
                + "rejection rate to report.");
    }

    // ------------------------------------------------------------------
    //  Report 3 - quality by production line (FR-10.7)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReportResponse productionQualityReport() {
        List<String> columns = List.of("Production line", "Batches", "Defects", "Units affected",
                "Defects per batch");

        Map<String, long[]> perLine = new TreeMap<>();   // line -> [batches, defects, units]
        Map<Integer, String> batchLine = new LinkedHashMap<>();

        for (ProductionBatch batch : batchRepository.findAllByOrderByBatchNumberDesc()) {
            batchLine.put(batch.getBatchId(), batch.getProductionLine());
            perLine.computeIfAbsent(batch.getProductionLine(), k -> new long[3])[0] += 1;
        }
        for (DefectResponse d : defectService.findAll()) {
            String line = d.batchId() == null ? "(no batch recorded)" : batchLine.get(d.batchId());
            if (line == null) {
                line = "(no batch recorded)";
            }
            long[] row = perLine.computeIfAbsent(line, k -> new long[3]);
            row[1] += 1;
            row[2] += d.unitsAffected();
        }

        List<ReportRow> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : perLine.entrySet()) {
            long[] t = entry.getValue();
            rows.add(new ReportRow(List.of(
                    entry.getKey(),
                    String.valueOf(t[0]),
                    String.valueOf(t[1]),
                    String.valueOf(t[2]),
                    String.format("%.2f", safePercentage(t[1], t[0])))));
        }
        rows.sort(Comparator.comparing(
                row -> row.cells().get(2), Comparator.reverseOrder()));

        return build("Quality by Production Line", columns, rows,
                "No production data is available yet. Create a production batch first.");
    }

    // ------------------------------------------------------------------
    //  Report 4 - overdue corrective actions (FR-08.8, FR-10.8)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReportResponse overdueReport() {
        List<String> columns = List.of("Defect", "Product", "Responsible person",
                "Action", "Target date", "Days overdue", "Progress");

        List<ReportRow> rows = new ArrayList<>();
        for (CorrectiveActionResponse a : actionRepository
                .findOverdue(LocalDate.now(), com.project.qms.entity.ProgressStatus.COMPLETED)
                .stream().map(CorrectiveActionService::toResponse).toList()) {
            rows.add(new ReportRow(List.of(
                    a.defectRef() == null ? "" : a.defectRef(),
                    "",
                    a.responsiblePerson() == null ? "" : a.responsiblePerson(),
                    a.actionDescription(),
                    a.targetDate() == null ? "" : a.targetDate().format(DATE),
                    String.valueOf(a.daysOverdue()),
                    a.progressStatus())));
        }

        return build("Overdue Corrective Actions", columns, rows,
                "Nothing is overdue. Every corrective action is either finished or "
                + "still within its target date.");
    }

    // ------------------------------------------------------------------

    private static ReportResponse build(String type, List<String> columns,
                                        List<ReportRow> rows, String emptyMessage) {
        String message = rows.isEmpty() ? emptyMessage : null;   // FR-10.11
        return new ReportResponse(
                type,
                LocalDateTime.now().format(STAMP),
                message,
                columns,
                rows);
    }

    /** Exposed so the controller can list what the report screen offers. */
    public List<String> availableReports() {
        return List.of("defect-register", "rejection-rate", "production-quality", "overdue-actions");
    }
}
