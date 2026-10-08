package com.project.qms.dto;

import java.util.List;

/**
 * One row of any report. Reports are tabular by nature, and every report
 * screen renders the same table, so one row type serves all of them.
 */
public record ReportRow(List<String> cells) {
}
