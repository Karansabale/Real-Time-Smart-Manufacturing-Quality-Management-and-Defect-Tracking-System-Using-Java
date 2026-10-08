package com.project.qms.controller;

import com.project.qms.dto.ReportResponse;
import com.project.qms.service.ReportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * The four reports (FR-10).
 *
 * All four return the same shape (columns + rows), which is why the report
 * screen needs one table renderer rather than four. A report that finds
 * nothing returns a message rather than an empty grid (FR-10.11).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** FR-10.15 - tells the screen which reports exist. */
    @GetMapping
    public ResponseEntity<List<String>> available(HttpSession session) {
        AuthController.currentUserId(session);
        return ResponseEntity.ok(reportService.availableReports());
    }

    /** FR-10.1, FR-10.2 - the defect register, with optional filters. */
    @GetMapping("/defects")
    public ResponseEntity<ReportResponse> defectRegister(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer productId,
            HttpSession session) {
        AuthController.currentUserId(session);
        return ResponseEntity.ok(reportService.defectReport(status, severity, productId));
    }

    /** FR-10.5, FR-10.6 - rejection rate per product. */
    @GetMapping("/rejection-rate")
    public ResponseEntity<ReportResponse> rejectionRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpSession session) {
        AuthController.currentUserId(session);
        return ResponseEntity.ok(reportService.inspectionReport(from, to));
    }

    /** FR-10.7 - quality by production line. */
    @GetMapping("/production-quality")
    public ResponseEntity<ReportResponse> productionQuality(HttpSession session) {
        AuthController.currentUserId(session);
        return ResponseEntity.ok(reportService.productionQualityReport());
    }

    /** FR-10.8 - what is overdue and who owns it. */
    @GetMapping("/overdue-actions")
    public ResponseEntity<ReportResponse> overdueActions(HttpSession session) {
        AuthController.currentUserId(session);
        return ResponseEntity.ok(reportService.overdueReport());
    }
}
