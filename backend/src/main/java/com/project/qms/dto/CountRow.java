package com.project.qms.dto;

/**
 * One bar of a dashboard chart: a label and how many.
 * Used for defects-by-severity and defects-by-category, which is why the
 * frontend needs only one chart-rendering routine instead of two.
 */
public record CountRow(String label, long count) {
}
