package com.project.qms.dto;

import java.util.List;

/**
 * Everything on the dashboard in one response (FR-09).
 *
 * One request instead of six: the counts, the two breakdowns and the recent
 * defects all arrive together, so the screen paints once. Every number is
 * computed live from the database on each call, never cached (FR-09.12).
 */
public record DashboardSummaryResponse(
        long totalDefects,
        long openDefects,
        long underInvestigation,
        long correctiveAction,
        long verified,
        long closed,
        long overdueActions,
        long activeProducts,
        long totalBatches,
        List<CountRow> defectsBySeverity,
        List<CountRow> defectsByCategory,
        List<DefectResponse> recentDefects) {
}
