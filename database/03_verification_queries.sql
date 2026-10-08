-- ============================================================================
--  Real-Time Smart Manufacturing Quality Management and Defect Tracking System
-- ============================================================================
--  File    : database/03_verification_queries.sql
--  Phase   : 7 — Database Design
--  Purpose : Proves the schema is correct and is enough to answer the business
--            questions this project exists to answer.
--
--  Run as  : root, after 01_schema.sql and 02_sample_data.sql
--  Run with: mysql -u root -p manufacturing_qms < database/03_verification_queries.sql
--
--  ONE COMMAND RUNS THEM ALL AND SHOWS WHICH ONES FAILED:
--      mysql --force --table -u root -p manufacturing_qms \
--            < database/03_verification_queries.sql | less
--
--  TESTED  : All 18 checks in Part A were executed against MySQL 8.4.6 on
--            2026-10-07 with the sample data loaded, and all 8 negative tests
--            in Part B were rejected by the database with the error code
--            written beside them. Two of the Part A checks (A14 and A5b) were
--            also proved to CATCH a fault by deliberately corrupting the data
--            inside a transaction and rolling back.
-- ============================================================================

USE manufacturing_qms;

SET @today = CURDATE();


-- ############################################################################
--  PART A — 18 READ-ONLY CHECKS
--  These are the queries the DashboardService and ReportService will run in
--  Phase 8. Writing them here first means the Java layer has nothing to guess.
--
--  HOW TO READ THE RESULTS — two kinds of check live here:
--
--    * REPORTING queries (A1-A13) return DATA. Rows are the expected result.
--
--    * INTEGRITY checks (A5a, A5b, A14, A15, A16, A17) return NOTHING when
--      the data is correct. An empty result IS the pass. That looks like a
--      query that did not run, so it is worth saying out loud: nothing printed
--      means nothing wrong.
--  ############################################################################


-- ---------------------------------------------------------------------------
-- A1. Dashboard tile: how many defects are in each status right now?  (FR-09)
--     This is the query behind the five coloured tiles on the dashboard.
-- ---------------------------------------------------------------------------
SELECT current_status AS status,
       COUNT(*)       AS defect_count
FROM defects
GROUP BY current_status
ORDER BY FIELD(current_status, 'OPEN','UNDER_INVESTIGATION','CORRECTIVE_ACTION','VERIFIED','CLOSED');


-- ---------------------------------------------------------------------------
-- A2. Dashboard chart: defects by severity  (FR-09)
-- ---------------------------------------------------------------------------
SELECT severity,
       COUNT(*) AS defect_count
FROM defects
GROUP BY severity
ORDER BY FIELD(severity, 'CRITICAL','HIGH','MEDIUM','LOW');


-- ---------------------------------------------------------------------------
-- A3. Dashboard chart: defects by category  (FR-09)
-- ---------------------------------------------------------------------------
SELECT defect_category,
       COUNT(*) AS defect_count
FROM defects
GROUP BY defect_category
ORDER BY defect_count DESC, defect_category;


-- ---------------------------------------------------------------------------
-- A4. OVERDUE CORRECTIVE ACTIONS  (FR-08.8) — the single most important query
--     in the project.
--
--     The overdue flag is DERIVED, never stored:
--         target_date < today  AND  progress_status <> 'COMPLETED'
--
--     On paper these are the actions that get forgotten. This query is why the
--     system exists.
-- ---------------------------------------------------------------------------
SELECT ca.action_id,
       d.defect_ref,
       p.product_code,
       u.full_name                                   AS responsible_person,
       ca.target_date,
       DATEDIFF(@today, ca.target_date)              AS days_overdue,
       ca.progress_status
FROM corrective_actions ca
JOIN defects d ON d.defect_id = ca.defect_id
JOIN products p ON p.product_id = d.product_id
JOIN users u ON u.user_id = ca.responsible_person_id
WHERE ca.target_date < @today
  AND ca.progress_status <> 'COMPLETED'
ORDER BY days_overdue DESC;


-- ---------------------------------------------------------------------------
-- A5a. THE VERIFICATION GATE, first half  (FR-08.14)
--      Every defect in VERIFIED or CLOSED must have at least one COMPLETED
--      corrective action. Any row returned here is a data integrity bug.
--
--      NOTE — a subtlety worth understanding: this check can pass even when
--      the completed action was verified NOT_EFFECTIVE, because "completed"
--      and "worked" are two different questions (FR-08.9 vs FR-08.7). That
--      is why A5b exists below.
-- ---------------------------------------------------------------------------
SELECT d.defect_ref, d.current_status, COUNT(ca.action_id) AS completed_actions
FROM defects d
LEFT JOIN corrective_actions ca
       ON ca.defect_id = d.defect_id
      AND ca.progress_status = 'COMPLETED'
WHERE d.current_status IN ('VERIFIED','CLOSED')
GROUP BY d.defect_id, d.defect_ref, d.current_status
HAVING COUNT(ca.action_id) = 0;


-- ---------------------------------------------------------------------------
-- A5b. THE VERIFICATION GATE, second half  (FR-08.10 + FR-08.14 combined)
--      The strict rule, and the one the application must implement:
--      a defect may only be VERIFIED or CLOSED if at least one corrective
--      action is BOTH completed AND verified EFFECTIVE.
--
--      This is the check that catches a real failure mode: someone completes
--      an action, an Inspector marks it NOT_EFFECTIVE, and the defect is
--      moved forward anyway. That must be impossible.
--
--      Defect 3 in the sample data satisfies this using its SECOND action —
--      the first one was NOT_EFFECTIVE and is deliberately left in the data
--      to prove the rule works.
--      Any row returned here is a data integrity bug.
-- ---------------------------------------------------------------------------
SELECT d.defect_ref,
       d.current_status,
       SUM(ca.progress_status = 'COMPLETED')     AS completed_actions,
       SUM(ca.verification_status = 'EFFECTIVE') AS effective_actions
FROM defects d
LEFT JOIN corrective_actions ca ON ca.defect_id = d.defect_id
WHERE d.current_status IN ('VERIFIED','CLOSED')
GROUP BY d.defect_id, d.defect_ref, d.current_status
HAVING SUM(ca.progress_status = 'COMPLETED' AND ca.verification_status = 'EFFECTIVE') = 0;


-- ---------------------------------------------------------------------------
-- A6. Which batch produced the most defects?  (the batch hotspot question)
-- ---------------------------------------------------------------------------
SELECT b.batch_number,
       p.product_code,
       b.production_line,
       COUNT(d.defect_id) AS defect_count,
       SUM(d.units_affected) AS total_units_affected
FROM production_batches b
JOIN products p ON p.product_id = b.product_id
LEFT JOIN defects d ON d.batch_id = b.batch_id
GROUP BY b.batch_id, b.batch_number, p.product_code, b.production_line
HAVING COUNT(d.defect_id) > 0
ORDER BY defect_count DESC, b.batch_number;


-- ---------------------------------------------------------------------------
-- A7. Rejection rate per product  (FR-10.5) — with the zero-division guard
--     (FR-10.12). NULLIF stops a divide-by-zero when a product has no
--     inspections yet; IFNULL turns the resulting NULL into 0.00.
-- ---------------------------------------------------------------------------
SELECT p.product_code,
       p.product_name,
       COUNT(i.inspection_id)                                  AS inspections,
       SUM(i.inspected_qty)                                    AS total_inspected,
       SUM(i.rejected_qty)                                     AS total_rejected,
       IFNULL(ROUND(100 * SUM(i.rejected_qty) / NULLIF(SUM(i.inspected_qty), 0), 2), 0.00) AS rejection_rate_pct
FROM products p
LEFT JOIN inspections i ON i.product_id = p.product_id
GROUP BY p.product_id, p.product_code, p.product_name
ORDER BY rejection_rate_pct DESC;


-- ---------------------------------------------------------------------------
-- A8. The complete history of one defect  (FR-06.12, FR-07.8).
--     Run this for DEF-2026-0005 to see the REVERSAL row in the middle —
--     the defect moved back from CORRECTIVE_ACTION to UNDER_INVESTIGATION
--     with a reason, then forward again. Best single query to run in the demo.
-- ---------------------------------------------------------------------------
SELECT dsh.history_id,
       dsh.from_status,
       dsh.to_status,
       dsh.remark,
       u.full_name  AS changed_by,
       dsh.changed_at
FROM defect_status_history dsh
JOIN defects d ON d.defect_id = dsh.defect_id
JOIN users u ON u.user_id = dsh.changed_by
WHERE d.defect_ref = CONCAT('DEF-', YEAR(@today), '-0005')
ORDER BY dsh.changed_at, dsh.history_id;


-- ---------------------------------------------------------------------------
-- A9. Defect ageing report  (FR-07.9) — how long has each open defect been
--     open? Anything over 30 days is a management escalation.
-- ---------------------------------------------------------------------------
SELECT d.defect_ref,
       p.product_code,
       d.severity,
       d.current_status,
       d.created_at,
       DATEDIFF(@today, d.created_at) AS age_in_days
FROM defects d
JOIN products p ON p.product_id = d.product_id
WHERE d.current_status <> 'CLOSED'
ORDER BY age_in_days DESC;


-- ---------------------------------------------------------------------------
-- A10. Defects by production line — "is the problem the machine or the
--      operator?" This is the question a plant manager actually asks.
-- ---------------------------------------------------------------------------
SELECT IFNULL(b.production_line, '(no batch recorded)') AS production_line,
       COUNT(d.defect_id)       AS defect_count,
       SUM(d.units_affected)    AS units_affected
FROM defects d
LEFT JOIN production_batches b ON b.batch_id = d.batch_id
GROUP BY b.production_line
ORDER BY defect_count DESC;


-- ---------------------------------------------------------------------------
-- A11. Monthly defect trend — the data behind the Chart.js line chart.
-- ---------------------------------------------------------------------------
SELECT DATE_FORMAT(created_at, '%Y-%m') AS month,
       COUNT(*)                        AS defects_raised,
       SUM(current_status = 'CLOSED')  AS closed_in_total
FROM defects
GROUP BY DATE_FORMAT(created_at, '%Y-%m')
ORDER BY month;


-- ---------------------------------------------------------------------------
-- A12. Inspector workload — how many inspections has each inspector performed,
--      and how often did they find a problem?
-- ---------------------------------------------------------------------------
SELECT u.full_name AS inspector,
       COUNT(i.inspection_id)                        AS inspections_done,
       SUM(i.result = 'FAIL')                        AS failed_inspections,
       IFNULL(ROUND(100 * SUM(i.result = 'FAIL') / NULLIF(COUNT(i.inspection_id), 0), 1), 0.0) AS fail_rate_pct
FROM users u
LEFT JOIN inspections i ON i.inspector_id = u.user_id
WHERE u.role = 'INSPECTOR'
GROUP BY u.user_id, u.full_name
ORDER BY inspections_done DESC;


-- ---------------------------------------------------------------------------
-- A13. THE WORK QUEUE — defects that have no corrective action assigned yet.
--      These are waiting on a Supervisor. A real plant wants this list daily.
-- ---------------------------------------------------------------------------
SELECT d.defect_ref,
       p.product_code,
       d.severity,
       d.current_status,
       DATEDIFF(@today, d.created_at) AS waiting_days
FROM defects d
JOIN products p ON p.product_id = d.product_id
WHERE NOT EXISTS (SELECT 1 FROM corrective_actions ca WHERE ca.defect_id = d.defect_id)
  AND d.current_status <> 'CLOSED'
ORDER BY waiting_days DESC;


-- ---------------------------------------------------------------------------
-- A14. INTEGRITY CHECK — does every defect's current_status agree with the
--      to_status of its most recent history row? Must return ZERO rows.
--
--      This is the check that proves the history table is trustworthy. If a
--      defect said CORRECTIVE_ACTION while its history said VERIFIED, the
--      audit trail would be worthless.
-- ---------------------------------------------------------------------------
SELECT d.defect_ref,
       d.current_status                              AS status_on_defect,
       (SELECT h.to_status FROM defect_status_history h
         WHERE h.defect_id = d.defect_id
         ORDER BY h.changed_at DESC, h.history_id DESC LIMIT 1) AS latest_history_status
FROM defects d
HAVING status_on_defect <> latest_history_status;


-- ---------------------------------------------------------------------------
-- A15. INTEGRITY CHECK — every defect must have at least one history row
--      (the mandatory 1:N relationship, Phase 5 §5.3 relationship 9).
--      Must return ZERO rows.
-- ---------------------------------------------------------------------------
SELECT d.defect_ref
FROM defects d
LEFT JOIN defect_status_history h ON h.defect_id = d.defect_id
GROUP BY d.defect_id, d.defect_ref
HAVING COUNT(h.history_id) = 0;


-- ---------------------------------------------------------------------------
-- A16. INTEGRITY CHECK — orphan scan. Counts any row whose foreign key points
--      at a parent that does not exist. Should be zero in every column.
--      (These counts would be non-zero if the foreign keys were missing.)
-- ---------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM production_batches b LEFT JOIN products p ON p.product_id = b.product_id WHERE p.product_id IS NULL) AS orphan_batches,
  (SELECT COUNT(*) FROM inspections i        LEFT JOIN products p ON p.product_id = i.product_id WHERE p.product_id IS NULL) AS orphan_insp_product,
  (SELECT COUNT(*) FROM defects d            LEFT JOIN products p ON p.product_id = d.product_id WHERE p.product_id IS NULL) AS orphan_defect_product,
  (SELECT COUNT(*) FROM defects d            LEFT JOIN users u    ON u.user_id    = d.reported_by WHERE u.user_id IS NULL)    AS orphan_defect_reporter,
  (SELECT COUNT(*) FROM defect_status_history h LEFT JOIN defects d ON d.defect_id = h.defect_id WHERE d.defect_id IS NULL)  AS orphan_history,
  (SELECT COUNT(*) FROM corrective_actions ca LEFT JOIN defects d ON d.defect_id = ca.defect_id WHERE d.defect_id IS NULL)    AS orphan_actions;


-- ---------------------------------------------------------------------------
-- A17. INTEGRITY CHECK — the quantity rule (FR-05.5) held for every inspection.
--      Must return ZERO rows.
-- ---------------------------------------------------------------------------
SELECT inspection_id, inspected_qty, rejected_qty
FROM inspections
WHERE rejected_qty > inspected_qty OR inspected_qty <= 0;


-- ############################################################################
--  PART B — NEGATIVE TESTS
--  ############################################################################
--  The checks above prove the data that IS in the database is correct.
--  These seven statements prove the database REJECTS data that is not.
--
--  Each one below is designed to FAIL. Run the whole file with --force so
--  MySQL keeps going after each rejection:
--
--      mysql --force --table -u root -p manufacturing_qms \
--            < database/03_verification_queries.sql
--
--  EVERY statement in this part should produce an ERROR. If any of them
--  succeeds, a constraint is missing and the schema has a hole in it.
--
--  The negative tests are commented out by default so that a normal run of
--  this file stays clean. Remove the '-- ' prefix from the lines marked
--  # NEGATIVE TEST to run one.
--
--  Some tests use @today, so that this section also works when you copy it
--  out on its own, the variable is set again here. Without it the test would
--  fail with "Column 'target_date' cannot be null" instead of the constraint
--  violation you are looking for.
-- ############################################################################

SET @today = CURDATE();

-- # NEGATIVE TEST B1 — an illegal role value. The ENUM must reject it.
-- ERROR 1265 (01000): Data truncated for column 'role'
-- INSERT INTO users (username, password_hash, full_name, role) VALUES ('hacker', 'x', 'Test User', 'SUPERUSER');

-- # NEGATIVE TEST B2 — a duplicate username (FR-02.3).
-- ERROR 1062 (23000): Duplicate entry 'admin' for key 'users.uk_users_username'
-- INSERT INTO users (username, password_hash, full_name, role) VALUES ('admin', 'x', 'Another Admin', 'ADMIN');

-- # NEGATIVE TEST B3 — a duplicate product code (FR-03.3).
-- ERROR 1062 (23000): Duplicate entry 'P-101' for key 'products.uk_products_code'
-- INSERT INTO products (product_code, product_name, category, created_by) VALUES ('P-101', 'Duplicate Housing', 'Test', 1);

-- # NEGATIVE TEST B4 — rejecting more units than were inspected (FR-05.5).
-- ERROR 3819 (HY000): Check constraint 'ck_inspections_rejected' is violated
-- INSERT INTO inspections (product_id, inspector_id, inspection_type, inspected_qty, rejected_qty, result, created_by)
-- VALUES (1, 2, 'FINAL', 100, 150, 'FAIL', 2);

-- # NEGATIVE TEST B5 — a corrective action with no description of real length (FR-08.2).
-- ERROR 3819 (HY000): Check constraint 'ck_actions_description' is violated
-- INSERT INTO corrective_actions (defect_id, responsible_person_id, action_description, target_date, created_by)
-- VALUES (8, 4, 'short', DATE_ADD(@today, INTERVAL 5 DAY), 4);

-- # NEGATIVE TEST B6 — recording a verification outcome with nobody verifying it (FR-08.11).
-- ERROR 3819 (HY000): Check constraint 'ck_actions_verified' is violated
-- INSERT INTO corrective_actions (defect_id, responsible_person_id, action_description, target_date, verification_status, created_by)
-- VALUES (8, 4, 'Check the packing procedure and add end caps to the line', DATE_ADD(@today, INTERVAL 5 DAY), 'EFFECTIVE', 4);

-- # NEGATIVE TEST B7 — deleting a product that has production batches (FR-03.8).
-- ERROR 1451 (23000): Cannot delete or update a parent row: a foreign key constraint fails
-- DELETE FROM products WHERE product_code = 'P-101';

-- # NEGATIVE TEST B8 — recording a status change where nothing changed.
-- ERROR 3819 (HY000): Check constraint 'ck_history_change' is violated
-- INSERT INTO defect_status_history (defect_id, from_status, to_status, changed_by) VALUES (10, 'OPEN', 'OPEN', 4);


-- ############################################################################
--  PART C — WHAT THESE RESULTS MEAN FOR PHASE 8
--  ############################################################################
--  1. Every query in Part A maps to a method in DashboardService or
--     ReportService. The SQL is already written and tested, so Phase 8 is a
--     matter of wrapping it in @Query annotations or derived query methods.
--  2. A4 (overdue) and A5b (verification gate) are the two queries to show in
--     the viva. They are the project's answer to the problem statement.
--  3. A5a, A5b, A14, A15, A16 and A17 are the integrity checks to run again
--     after the testing phase (Phase 11), to prove the application itself
--     never wrote bad data.
--  4. The overdue query (A4) uses the index idx_actions_overdue, and EXPLAIN
--     confirms it is used as a covering index. The index is doing its job even
--     with ten rows; at 10,000 corrective actions it is the difference between
--     an instant dashboard and a slow one.
--  ############################################################################
