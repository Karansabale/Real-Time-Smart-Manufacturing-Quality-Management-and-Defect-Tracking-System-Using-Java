package com.project.qms.controller;

import com.project.qms.dto.DashboardSummaryResponse;
import com.project.qms.service.DashboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The dashboard (FR-09). Read-only, so it needs no business rules. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Everything the dashboard shows, in one response (FR-09.1 to FR-09.12).
     *
     * Every number is recomputed on each call from the live database. There is
     * no cache, because a quality dashboard showing yesterday's figures is
     * worse than no dashboard at all.
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(HttpSession session) {
        AuthController.currentUserId(session);       // anyone logged in may see it
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}
