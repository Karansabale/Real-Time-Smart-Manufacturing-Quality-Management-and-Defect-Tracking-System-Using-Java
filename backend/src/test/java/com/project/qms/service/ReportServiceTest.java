package com.project.qms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the one piece of arithmetic the reports are built on:
 * {@code ReportService.safePercentage} (FR-10.12).
 *
 * <p>Why this method, and not the reports themselves? Two of the four reports
 * print a percentage, and every one of those numbers is produced here — the
 * rejection rate per product and the pass rate per inspection type. The rest of
 * a report is a lookup and a format, which the API test cases see as rows and
 * strings; the arithmetic is the part where a wrong answer is still a
 * perfectly well-formed answer, and nobody would notice.
 *
 * <p>The divide-by-zero case is the reason the method exists at all. A product
 * that has been inspected zero times must not print "NaN %" or crash the
 * report screen; the decision taken in Phase 8 was to answer 0.00, and that
 * decision is pinned down here so a later change cannot quietly reverse it.
 *
 * <p>Run:  cd backend && mvn test      (or mvn package, which runs them too)
 */
class ReportServiceTest {

    @Test
    @DisplayName("TC-UNIT-10  nobody inspected anything: 0.00, not NaN")
    void a_zero_denominator_gives_zero_not_not_a_number() {
        assertEquals(0.0, ReportService.safePercentage(0, 0), 0.0001);
    }

    @Test
    @DisplayName("TC-UNIT-11  three rejects out of twelve inspected is 25 %")
    void a_plain_share_comes_back_as_a_percentage() {
        assertEquals(25.0, ReportService.safePercentage(3, 12), 0.0001);
    }

    @Test
    @DisplayName("TC-UNIT-12  one in three is rounded to two decimals: 33.33, not 33.333333")
    void the_result_is_rounded_to_two_decimals() {
        assertEquals(33.33, ReportService.safePercentage(1, 3), 0.0001);
    }

    @Test
    @DisplayName("TC-UNIT-13  two in three rounds up: 66.67")
    void a_partial_share_rounds_to_the_nearest_hundredth() {
        assertEquals(66.67, ReportService.safePercentage(2, 3), 0.0001);
    }

    @Test
    @DisplayName("TC-UNIT-14  none rejected is 0 and all rejected is 100 — the two ends")
    void the_two_ends_of_the_scale_are_exact() {
        assertEquals(0.0, ReportService.safePercentage(0, 5), 0.0001);
        assertEquals(100.0, ReportService.safePercentage(5, 5), 0.0001);
    }
}
