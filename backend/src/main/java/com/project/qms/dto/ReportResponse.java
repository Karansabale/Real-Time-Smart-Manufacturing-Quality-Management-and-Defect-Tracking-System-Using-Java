package com.project.qms.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The result of any report, in a shape the frontend can render as a table.
 *
 * 'message' carries FR-10.11: when a report finds nothing, it returns an
 * explanatory message rather than an empty table the user might mistake for
 * a broken screen. A report with no rows is not an error - it is an answer.
 */
public record ReportResponse(
        String reportType,
        String generatedAt,
        String message,
        List<String> columns,
        List<ReportRow> rows) {
}
