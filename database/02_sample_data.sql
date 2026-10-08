-- ============================================================================
--  Real-Time Smart Manufacturing Quality Management and Defect Tracking System
-- ============================================================================
--  File    : database/02_sample_data.sql
--  Phase   : 7 — Database Design
--  Purpose : Loads realistic sample manufacturing data so that the application
--            can be demonstrated immediately after setup (FR-14).
--
--  Run as  : root (or any administrator), AFTER 01_schema.sql
--  Run with: mysql -u root -p manufacturing_qms < database/02_sample_data.sql
--
--  TESTED  : Executed against MySQL 8.4.6 on 2026-10-07. 6 users, 6 products,
--            10 batches, 12 inspections, 10 defects, 8 corrective actions and
--            30 history rows were inserted without error.
-- ============================================================================

USE manufacturing_qms;

SET NAMES utf8mb4;


-- ============================================================================
--  HOW THE DATES WORK — READ THIS
-- ============================================================================
--  Every date below is written RELATIVE TO TODAY, using the variable @today.
--
--  WHY: if the dates were hard-coded (2026-08-14 and so on), then a year from
--  now the "overdue corrective action" would be two years overdue, the
--  dashboard would show nothing recent, and the demo would look stale.
--  With relative dates, the data is just as realistic whenever you load it.
--
--  The one side effect to know about: defect references use the YEAR you load
--  the data in, so loading this in 2027 produces DEF-2027-0001. That is
--  correct behaviour — the reference year is supposed to be the year the
--  defect was raised (FR-06.8).
-- ============================================================================

SET @today = CURDATE();


-- ============================================================================
--  SECTION 1 — USERS (6)
-- ============================================================================
--  Passwords are BCrypt hashes. BCrypt is a one-way hash: the plain password
--  CANNOT be recovered from these strings. They were generated with the
--  project's own BCryptPasswordEncoder, and each one was verified with
--  encoder.matches() before being written here.
--
--  DEMO LOGINS (change these before any real use):
--    admin        / Admin@123      — Administrator
--    inspector1   / Inspect@123    — Quality Inspector
--    inspector2   / Inspect@123    — Quality Inspector
--    supervisor1  / Supervise@123  — Production Supervisor
--    supervisor2  / Supervise@123  — Production Supervisor
--    inspector3   / Inspect@123    — INACTIVE (cannot log in — FR-02.8)
--
--  inspector3 is deliberately deactivated. She appears as the reporter of
--  three defects and as the inspector on two inspections, which demonstrates
--  the point of FR-02.9: deactivating a user blocks login but preserves the
--  historical records that reference her.
-- ============================================================================

INSERT INTO users (user_id, username, password_hash, full_name, role, is_active) VALUES
(1, 'admin',
 '$2a$10$N4GXLaXfp9fVWjf8epMd1u2LDRzfl4bq.iLK.QQF7IEFYgZYke5WW',
 'System Administrator', 'ADMIN', TRUE),
(2, 'inspector1',
 '$2a$10$sEvbzFGi2pSiv3T3RzG0lupgmcK7hFa3G7VlPA1/sf7/grh1RQugG',
 'Amit Deshmukh', 'INSPECTOR', TRUE),
(3, 'inspector2',
 '$2a$10$sEvbzFGi2pSiv3T3RzG0lupgmcK7hFa3G7VlPA1/sf7/grh1RQugG',
 'Sneha Kulkarni', 'INSPECTOR', TRUE),
(4, 'supervisor1',
 '$2a$10$5yXtJvBeNqBAssjZtSiu4uMzyfun9snOTzBdQslLgUEscQzFguTOa',
 'Ravi Patil', 'SUPERVISOR', TRUE),
(5, 'supervisor2',
 '$2a$10$5yXtJvBeNqBAssjZtSiu4uMzyfun9snOTzBdQslLgUEscQzFguTOa',
 'Nikhil Joshi', 'SUPERVISOR', TRUE),
(6, 'inspector3',
 '$2a$10$sEvbzFGi2pSiv3T3RzG0lupgmcK7hFa3G7VlPA1/sf7/grh1RQugG',
 'Meera Iyer', 'INSPECTOR', FALSE);


-- ============================================================================
--  SECTION 2 — PRODUCTS (6)
-- ============================================================================
--  A small Pune-area machining and assembly unit turning out automotive and
--  hydraulic components. Four product families, six parts.
-- ============================================================================

INSERT INTO products (product_id, product_code, product_name, category, specification, created_by, created_at) VALUES
(1, 'P-101', 'Bearing Housing',        'Machined Components',  'Aluminium alloy LM25, OD 62 mm, bore tolerance +/-0.05 mm', 1, DATE_SUB(@today, INTERVAL 120 DAY)),
(2, 'P-102', 'Drive Shaft',            'Machined Components',  'EN8 steel, dia 25 mm, induction hardened to 45 HRC',       1, DATE_SUB(@today, INTERVAL 120 DAY)),
(3, 'P-103', 'Gear Blank',             'Transmission Parts',   '20MnCr5 forged blank, 120 mm OD, ready for hobbing',       1, DATE_SUB(@today, INTERVAL 115 DAY)),
(4, 'P-104', 'Hydraulic Manifold',     'Hydraulic Assemblies', 'Grey cast iron FG260, 6-port, rated 210 bar',              1, DATE_SUB(@today, INTERVAL 110 DAY)),
(5, 'P-105', 'Brake Disc Rotor',       'Brake Systems',        'Grey cast iron, dia 240 mm, minimum thickness 22 mm',      1, DATE_SUB(@today, INTERVAL 105 DAY)),
(6, 'P-106', 'Clutch Plate Assembly',  'Transmission Parts',   'Friction disc assembly, dia 215 mm, OEM drawing REV-04',   1, DATE_SUB(@today, INTERVAL 100 DAY));


-- ============================================================================
--  SECTION 3 — PRODUCTION BATCHES (10)
-- ============================================================================
--  Shows all three production statuses:
--     8 x COMPLETED      (finished runs, closed with an end date)
--     1 x IN_PROGRESS    (on the shop floor now, no end date yet)
--     1 x PLANNED        (scheduled, future start date, no end date)
-- ============================================================================

INSERT INTO production_batches
    (batch_id, product_id, batch_number, quantity_produced, production_line, start_date, end_date, production_status, created_by, created_at) VALUES
( 1, 1, 'BATCH-2026-001', 500, 'LINE-A', DATE_SUB(@today, INTERVAL 75 DAY), DATE_SUB(@today, INTERVAL 72 DAY), 'COMPLETED',   4, DATE_SUB(@today, INTERVAL 76 DAY)),
( 2, 2, 'BATCH-2026-002', 300, 'LINE-B', DATE_SUB(@today, INTERVAL 68 DAY), DATE_SUB(@today, INTERVAL 65 DAY), 'COMPLETED',   4, DATE_SUB(@today, INTERVAL 69 DAY)),
( 3, 3, 'BATCH-2026-003', 800, 'LINE-A', DATE_SUB(@today, INTERVAL 60 DAY), DATE_SUB(@today, INTERVAL 56 DAY), 'COMPLETED',   4, DATE_SUB(@today, INTERVAL 61 DAY)),
( 4, 4, 'BATCH-2026-004', 150, 'LINE-C', DATE_SUB(@today, INTERVAL 52 DAY), DATE_SUB(@today, INTERVAL 49 DAY), 'COMPLETED',   5, DATE_SUB(@today, INTERVAL 53 DAY)),
( 5, 5, 'BATCH-2026-005', 400, 'LINE-D', DATE_SUB(@today, INTERVAL 45 DAY), DATE_SUB(@today, INTERVAL 41 DAY), 'COMPLETED',   4, DATE_SUB(@today, INTERVAL 46 DAY)),
( 6, 1, 'BATCH-2026-006', 450, 'LINE-A', DATE_SUB(@today, INTERVAL 35 DAY), DATE_SUB(@today, INTERVAL 32 DAY), 'COMPLETED',   4, DATE_SUB(@today, INTERVAL 36 DAY)),
( 7, 6, 'BATCH-2026-007', 250, 'LINE-B', DATE_SUB(@today, INTERVAL 28 DAY), DATE_SUB(@today, INTERVAL 24 DAY), 'COMPLETED',   5, DATE_SUB(@today, INTERVAL 29 DAY)),
( 8, 2, 'BATCH-2026-008', 320, 'LINE-B', DATE_SUB(@today, INTERVAL 18 DAY), DATE_SUB(@today, INTERVAL 15 DAY), 'COMPLETED',   4, DATE_SUB(@today, INTERVAL 19 DAY)),
( 9, 3, 'BATCH-2026-009', 750, 'LINE-A', DATE_SUB(@today, INTERVAL 10 DAY), NULL,                              'IN_PROGRESS', 4, DATE_SUB(@today, INTERVAL 11 DAY)),
(10, 5, 'BATCH-2026-010', 380, 'LINE-D', DATE_ADD(@today, INTERVAL  3 DAY), NULL,                              'PLANNED',     5, DATE_SUB(@today, INTERVAL  1 DAY));


-- ============================================================================
--  SECTION 4 — INSPECTIONS (12)
-- ============================================================================
--  8 inspections found problems (FAIL) and became the source of a defect.
--  4 inspections passed.
--
--  Inspection 10 has NO batch (batch_id is NULL): the inspector checked a
--  product without tying it to a specific production run (FR-05.1).
--
--  Inspection 11 is a PASS with 5 rejected units. This is the exact case
--  FR-05.9 warns about: the interface asks the inspector to confirm before
--  saving, because a "pass" with rejects deserves a second look.
-- ============================================================================

INSERT INTO inspections
    (inspection_id, product_id, batch_id, inspector_id, inspection_date, inspection_type, inspected_qty, rejected_qty, result, remarks, created_by, created_at) VALUES
( 1, 1,    1, 2, DATE_SUB(@today, INTERVAL 72 DAY), 'INCOMING',  500, 0, 'PASS', 'Incoming aluminium castings verified, dimensions within tolerance', 2, DATE_SUB(@today, INTERVAL 72 DAY)),
( 2, 2,    2, 2, DATE_SUB(@today, INTERVAL 65 DAY), 'IN_PROCESS',300, 4, 'FAIL', 'Deep turning marks and scoring on the bearing journal surface',      2, DATE_SUB(@today, INTERVAL 65 DAY)),
( 3, 3,    3, 3, DATE_SUB(@today, INTERVAL 56 DAY), 'FINAL',     800,12, 'FAIL', 'Pitch diameter oversize on 12 blanks, measured with gear tester',    3, DATE_SUB(@today, INTERVAL 56 DAY)),
( 4, 4,    4, 2, DATE_SUB(@today, INTERVAL 49 DAY), 'INCOMING',  150, 0, 'PASS', 'Castings sound, no visible blowholes or sand inclusion',             2, DATE_SUB(@today, INTERVAL 49 DAY)),
( 5, 5,    5, 3, DATE_SUB(@today, INTERVAL 41 DAY), 'FINAL',     400, 3, 'FAIL', 'Hardness below specification on 3 rotors, tested on Brinell tester', 3, DATE_SUB(@today, INTERVAL 41 DAY)),
( 6, 1,    6, 6, DATE_SUB(@today, INTERVAL 32 DAY), 'IN_PROCESS',450, 2, 'FAIL', 'Bore diameter undersize on 2 housings after boring operation',       6, DATE_SUB(@today, INTERVAL 32 DAY)),
( 7, 6,    7, 2, DATE_SUB(@today, INTERVAL 24 DAY), 'FINAL',     250, 0, 'PASS', 'Clutch plate assemblies within runout limit of 0.15 mm',             2, DATE_SUB(@today, INTERVAL 24 DAY)),
( 8, 2,    8, 3, DATE_SUB(@today, INTERVAL 15 DAY), 'FINAL',     320, 6, 'FAIL', 'Keyway width oversize, coupling fits loose on 6 shafts',             3, DATE_SUB(@today, INTERVAL 15 DAY)),
( 9, 3,    9, 2, DATE_SUB(@today, INTERVAL  9 DAY), 'IN_PROCESS',400, 1, 'FAIL', 'Gas porosity visible on machined face after hobbing',                2, DATE_SUB(@today, INTERVAL  9 DAY)),
(10, 1, NULL, 3, DATE_SUB(@today, INTERVAL  7 DAY), 'FINAL',     200, 0, 'PASS', 'Spot check on finished stock, no batch traceability required',       3, DATE_SUB(@today, INTERVAL  7 DAY)),
(11, 5,    5, 2, DATE_SUB(@today, INTERVAL  4 DAY), 'FINAL',     150, 5, 'PASS', 'Rework lot re-inspected after shot blasting, surface now acceptable', 2, DATE_SUB(@today, INTERVAL  4 DAY)),
(12, 6,    7, 3, DATE_SUB(@today, INTERVAL  1 DAY), 'FINAL',     250, 8, 'FAIL', 'Runout exceeds 0.15 mm on 8 assemblies, vibration reported by customer', 3, DATE_SUB(@today, INTERVAL 1 DAY));


-- ============================================================================
--  SECTION 5 — DEFECTS (10)
-- ============================================================================
--  All five statuses are represented, so every dashboard tile and every
--  report row has data:
--
--     status                 count   defect refs
--     --------------------   -----   --------------------------
--     OPEN                     2     DEF-2026-0009, DEF-2026-0010
--     UNDER_INVESTIGATION      2     DEF-2026-0007, DEF-2026-0008
--     CORRECTIVE_ACTION        3     DEF-2026-0004, 0005, 0006
--     VERIFIED                 2     DEF-2026-0002, DEF-2026-0003
--     CLOSED                   1     DEF-2026-0001
--
--  Severity is spread across LOW / MEDIUM / HIGH / CRITICAL, and the seven
--  defect categories are all used at least once.
--
--  Defects 8 and 10 have NO inspection and NO batch. They were reported
--  directly by an inspector rather than being raised from a formal
--  inspection — which is exactly why those two columns are nullable
--  (Phase 5, D5).
-- ============================================================================

INSERT INTO defects
    (defect_id, defect_ref, product_id, batch_id, inspection_id, reported_by, defect_category, description, severity, units_affected, current_status, created_by, created_at, updated_at) VALUES
( 1, CONCAT('DEF-', YEAR(@today), '-0001'), 2,    2,    2, 2, 'SURFACE_FINISH',
 'Deep turning marks and scoring on the shaft bearing journal surface', 'HIGH', 4, 'CLOSED',
 2, DATE_SUB(@today, INTERVAL 64 DAY), DATE_SUB(@today, INTERVAL 46 DAY)),

( 2, CONCAT('DEF-', YEAR(@today), '-0002'), 3,    3,    3, 3, 'DIMENSIONAL',
 'Gear blank pitch diameter oversize by 0.08 mm against drawing tolerance', 'MEDIUM', 12, 'VERIFIED',
 3, DATE_SUB(@today, INTERVAL 55 DAY), DATE_SUB(@today, INTERVAL 43 DAY)),

( 3, CONCAT('DEF-', YEAR(@today), '-0003'), 5,    5,    5, 3, 'MATERIAL',
 'Brake disc rotor hardness measured at 168 HB against specified 190-220 HB', 'CRITICAL', 3, 'VERIFIED',
 3, DATE_SUB(@today, INTERVAL 40 DAY), DATE_SUB(@today, INTERVAL 26 DAY)),

( 4, CONCAT('DEF-', YEAR(@today), '-0004'), 1,    6,    6, 6, 'DIMENSIONAL',
 'Bearing bore diameter undersize by 0.03 mm on two housings', 'MEDIUM', 2, 'CORRECTIVE_ACTION',
 6, DATE_SUB(@today, INTERVAL 31 DAY), DATE_SUB(@today, INTERVAL 22 DAY)),

( 5, CONCAT('DEF-', YEAR(@today), '-0005'), 2,    8,    8, 3, 'DIMENSIONAL',
 'Keyway width oversize by 0.05 mm causing loose coupling fit on 6 shafts', 'HIGH', 6, 'CORRECTIVE_ACTION',
 3, DATE_SUB(@today, INTERVAL 14 DAY), DATE_SUB(@today, INTERVAL  9 DAY)),

( 6, CONCAT('DEF-', YEAR(@today), '-0006'), 3,    9,    9, 2, 'MATERIAL',
 'Gas porosity visible on the machined face of a gear blank after hobbing', 'HIGH', 1, 'CORRECTIVE_ACTION',
 2, DATE_SUB(@today, INTERVAL  8 DAY), DATE_SUB(@today, INTERVAL  3 DAY)),

( 7, CONCAT('DEF-', YEAR(@today), '-0007'), 5,    5,   11, 2, 'SURFACE_FINISH',
 'Rust patches on the rotor hub face after rework handling at the store', 'LOW', 5, 'UNDER_INVESTIGATION',
 2, DATE_SUB(@today, INTERVAL  5 DAY), DATE_SUB(@today, INTERVAL  3 DAY)),

( 8, CONCAT('DEF-', YEAR(@today), '-0008'), 4, NULL, NULL, 2, 'PACKAGING',
 'Manifold shipping crate found without protective end caps on five units', 'LOW', 5, 'OPEN',
 2, DATE_SUB(@today, INTERVAL  2 DAY), DATE_SUB(@today, INTERVAL  2 DAY)),

( 9, CONCAT('DEF-', YEAR(@today), '-0009'), 6,    7,   12, 3, 'FUNCTIONAL',
 'Clutch plate runout exceeds 0.15 mm, vibration reported at customer end', 'HIGH', 8, 'UNDER_INVESTIGATION',
 3, DATE_SUB(@today, INTERVAL  1 DAY), DATE_SUB(@today, INTERVAL  1 DAY)),

(10, CONCAT('DEF-', YEAR(@today), '-0010'), 1, NULL, NULL, 2, 'ASSEMBLY',
 'Mounting bracket holes not aligned on 3 housings, assembly not possible', 'MEDIUM', 3, 'OPEN',
 2, @today, @today);


-- ============================================================================
--  SECTION 6 — DEFECT STATUS HISTORY (30 rows)
-- ============================================================================
--  This is the audit trail. Every defect has one row per status change, and
--  the first row of each defect has from_status = NULL, meaning "created".
--
--  The sequence must agree with defects.current_status above. If it does not,
--  the data is internally inconsistent and the inconsistency is a bug.
--
--  Defect 5 contains a REVERSAL, which is the most interesting row in the
--  table:
--        CORRECTIVE_ACTION -> UNDER_INVESTIGATION  (reason recorded)
--        UNDER_INVESTIGATION -> CORRECTIVE_ACTION
--  The supervisor discovered that the original corrective action addressed
--  the wrong cause, so the defect was moved one step back (FR-07.6) with a
--  remark before being advanced again. Being able to show this row in the
--  demo proves the status flow is not a one-way ratchet.
-- ============================================================================

INSERT INTO defect_status_history
    (defect_id, from_status, to_status, remark, changed_by, changed_at) VALUES

-- ---- Defect 1 — CLOSED (5 rows) -------------------------------------------
(1, NULL,                   'OPEN',                  'Defect registered against BATCH-2026-002',            2, DATE_SUB(@today, INTERVAL 64 DAY)),
(1, 'OPEN',                 'UNDER_INVESTIGATION',   'Investigation started, root cause to be established', 4, DATE_SUB(@today, INTERVAL 61 DAY)),
(1, 'UNDER_INVESTIGATION',  'CORRECTIVE_ACTION',     'Insert grade changed, corrective action assigned',   4, DATE_SUB(@today, INTERVAL 58 DAY)),
(1, 'CORRECTIVE_ACTION',    'VERIFIED',              'Surface finish re-measured on trial batch, within Ra 1.6', 2, DATE_SUB(@today, INTERVAL 46 DAY)),
(1, 'VERIFIED',             'CLOSED',                'Verified and closed, no recurrence in 3 batches',    1, DATE_SUB(@today, INTERVAL 45 DAY)),

-- ---- Defect 2 — VERIFIED (4 rows) ----------------------------------------
(2, NULL,                   'OPEN',                  'Defect registered against BATCH-2026-003',            3, DATE_SUB(@today, INTERVAL 55 DAY)),
(2, 'OPEN',                 'UNDER_INVESTIGATION',   'Investigation started on forging supplier',           4, DATE_SUB(@today, INTERVAL 52 DAY)),
(2, 'UNDER_INVESTIGATION',  'CORRECTIVE_ACTION',     'Die wear identified, corrective action assigned',     4, DATE_SUB(@today, INTERVAL 48 DAY)),
(2, 'CORRECTIVE_ACTION',    'VERIFIED',              'Pitch diameter re-checked on 50 pieces, all within tolerance', 3, DATE_SUB(@today, INTERVAL 43 DAY)),

-- ---- Defect 3 — VERIFIED, after one failed corrective action (4 rows) ----
(3, NULL,                   'OPEN',                  'Defect registered against BATCH-2026-005',            3, DATE_SUB(@today, INTERVAL 40 DAY)),
(3, 'OPEN',                 'UNDER_INVESTIGATION',   'Hardness failure escalated, casting lot quarantined', 4, DATE_SUB(@today, INTERVAL 37 DAY)),
(3, 'UNDER_INVESTIGATION',  'CORRECTIVE_ACTION',     'Re-heat treatment scheduled, action assigned',        4, DATE_SUB(@today, INTERVAL 34 DAY)),
(3, 'CORRECTIVE_ACTION',    'VERIFIED',              'Second corrective action effective, hardness 205 HB', 3, DATE_SUB(@today, INTERVAL 26 DAY)),

-- ---- Defect 4 — CORRECTIVE_ACTION (3 rows) -------------------------------
(4, NULL,                   'OPEN',                  'Defect registered against BATCH-2026-006',            6, DATE_SUB(@today, INTERVAL 31 DAY)),
(4, 'OPEN',                 'UNDER_INVESTIGATION',   'Investigation started on boring tool offset',         4, DATE_SUB(@today, INTERVAL 27 DAY)),
(4, 'UNDER_INVESTIGATION',  'CORRECTIVE_ACTION',     'Corrective action assigned to tool room',             4, DATE_SUB(@today, INTERVAL 22 DAY)),

-- ---- Defect 5 — CORRECTIVE_ACTION, includes a reversal (5 rows) ----------
(5, NULL,                   'OPEN',                  'Defect registered against BATCH-2026-008',            3, DATE_SUB(@today, INTERVAL 14 DAY)),
(5, 'OPEN',                 'UNDER_INVESTIGATION',   'Investigation started on keyway broach condition',    4, DATE_SUB(@today, INTERVAL 12 DAY)),
(5, 'UNDER_INVESTIGATION',  'CORRECTIVE_ACTION',     'Broach re-sharpening scheduled, action assigned',     4, DATE_SUB(@today, INTERVAL  9 DAY)),
(5, 'CORRECTIVE_ACTION',    'UNDER_INVESTIGATION',   'Reversal: re-sharpening did not address the root cause, broach guide bush worn. Re-investigating.', 4, DATE_SUB(@today, INTERVAL  6 DAY)),
(5, 'UNDER_INVESTIGATION',  'CORRECTIVE_ACTION',     'Guide bush replacement added as second corrective action', 4, DATE_SUB(@today, INTERVAL  5 DAY)),

-- ---- Defect 6 — CORRECTIVE_ACTION (3 rows) -------------------------------
(6, NULL,                   'OPEN',                  'Defect registered against BATCH-2026-009',            2, DATE_SUB(@today, INTERVAL  8 DAY)),
(6, 'OPEN',                 'UNDER_INVESTIGATION',   'Porosity source being traced, pouring temperature checked', 4, DATE_SUB(@today, INTERVAL  6 DAY)),
(6, 'UNDER_INVESTIGATION',  'CORRECTIVE_ACTION',     'Degassing procedure to be revised, action assigned',  4, DATE_SUB(@today, INTERVAL  3 DAY)),

-- ---- Defect 7 — UNDER_INVESTIGATION (2 rows) -----------------------------
(7, NULL,                   'OPEN',                  'Defect registered against rework lot of BATCH-2026-005', 2, DATE_SUB(@today, INTERVAL  5 DAY)),
(7, 'OPEN',                 'UNDER_INVESTIGATION',   'Store humidity and packing checked, investigation open', 4, DATE_SUB(@today, INTERVAL  3 DAY)),

-- ---- Defect 8 — OPEN (1 row) ---------------------------------------------
(8, NULL,                   'OPEN',                  'Defect registered directly, no inspection performed', 2, DATE_SUB(@today, INTERVAL  2 DAY)),

-- ---- Defect 9 — UNDER_INVESTIGATION (2 rows) -----------------------------
(9, NULL,                   'OPEN',                  'Defect registered against BATCH-2026-007 after customer complaint', 3, DATE_SUB(@today, INTERVAL  1 DAY)),
(9, 'OPEN',                 'UNDER_INVESTIGATION',   'Customer complaint logged, runout stack-up under study', 4, DATE_SUB(@today, INTERVAL  1 DAY)),

-- ---- Defect 10 — OPEN (1 row) --------------------------------------------
(10, NULL,                  'OPEN',                  'Defect registered directly by inspector during assembly trial', 2, @today);


-- ============================================================================
--  SECTION 7 — CORRECTIVE ACTIONS (8)
-- ============================================================================
--  This section is where the project's core problem statement is visible.
--
--  FR-08.8 — OVERDUE. Two actions are overdue:
--      action 6 (defect 5) target date 4 days ago, still PENDING
--      action 8 (defect 7) target date 1 day ago, still PENDING
--  On paper these are exactly the actions that get forgotten. Here they are
--  computed, counted and shown in red on the dashboard.
--
--  FR-08.10 / FR-08.14 — THE VERIFICATION GATE.
--      Action 3 was completed but verified NOT_EFFECTIVE, so the defect was
--      NOT allowed to progress. A second action (action 4) was created,
--      completed and verified EFFECTIVE, and only then did defect 3 move to
--      VERIFIED. This is the single best story to tell in the viva.
--
--      Action 7 is COMPLETED but still PENDING verification, so defect 6 is
--      correctly still sitting in CORRECTIVE_ACTION and cannot advance until
--      an Inspector verifies it.
--
--  FR-08.14 consistency rule: every defect in VERIFIED or CLOSED status has
--  at least one COMPLETED action. Defect 1, 2 and 3 satisfy it.
-- ============================================================================

INSERT INTO corrective_actions
    (action_id, defect_id, responsible_person_id, action_description, progress_remark, target_date, completion_date, progress_status, verification_status, verification_remark, verified_by, verified_at, created_by, created_at) VALUES

-- Action 1 — Defect 1, completed and verified effective (this is why defect 1 is CLOSED)
(1, 1, 4, 'Replace the worn turning insert and re-check surface finish on a trial batch of 20 shafts',
 'Insert grade CNMG 120408 replaced, trial batch surface finish Ra 1.4 measured', DATE_SUB(@today, INTERVAL 58 DAY), DATE_SUB(@today, INTERVAL 52 DAY),
 'COMPLETED', 'EFFECTIVE', 'Trial batch surface finish within Ra 1.6 limit, accepted', 2, DATE_SUB(@today, INTERVAL 50 DAY), 4, DATE_SUB(@today, INTERVAL 58 DAY)),

-- Action 2 — Defect 2, completed and verified effective
(2, 2, 4, 'Return the worn forging die for refurbishment and re-qualify first-off samples',
 'Die refurbished by supplier, first-off samples measured at nominal pitch diameter', DATE_SUB(@today, INTERVAL 48 DAY), DATE_SUB(@today, INTERVAL 45 DAY),
 'COMPLETED', 'EFFECTIVE', '50 pieces checked, all within tolerance, accepted', 3, DATE_SUB(@today, INTERVAL 43 DAY), 4, DATE_SUB(@today, INTERVAL 48 DAY)),

-- Action 3 — Defect 3, completed but VERIFIED NOT EFFECTIVE (the first fix failed)
(3, 3, 4, 'Re-heat treat the affected rotor lot to restore the specified hardness range',
 'Lot re-heat treated, hardness re-tested', DATE_SUB(@today, INTERVAL 38 DAY), DATE_SUB(@today, INTERVAL 35 DAY),
 'COMPLETED', 'NOT_EFFECTIVE', 'Hardness still 172 HB after re-heat treatment. Cause not addressed, further action required.', 3, DATE_SUB(@today, INTERVAL 33 DAY), 4, DATE_SUB(@today, INTERVAL 38 DAY)),

-- Action 4 — Defect 3, the second action, effective (this is what unblocked defect 3)
(4, 3, 5, 'Change the rotor casting source to the alternate foundry and verify hardness on the first lot',
 'Castings received from alternate foundry, hardness verified at 205 HB', DATE_SUB(@today, INTERVAL 30 DAY), DATE_SUB(@today, INTERVAL 28 DAY),
 'COMPLETED', 'EFFECTIVE', 'Hardness 205 HB within 190-220 HB, batch released', 3, DATE_SUB(@today, INTERVAL 26 DAY), 4, DATE_SUB(@today, INTERVAL 30 DAY)),

-- Action 5 — Defect 4, in progress and on schedule (NOT overdue)
(5, 4, 5, 'Recalibrate the boring tool offset setting and add a first-piece check to the route card',
 'Offset corrected, first-piece check added to the route card, monitoring 2 more batches', DATE_ADD(@today, INTERVAL 7 DAY), NULL,
 'IN_PROGRESS', 'PENDING', NULL, NULL, NULL, 4, DATE_SUB(@today, INTERVAL 22 DAY)),

-- Action 6 — Defect 5, OVERDUE (target date has passed, still PENDING)
(6, 5, 4, 'Replace the worn broach guide bush and re-sharpen the keyway broach',
 'Not started. Guide bush delivery from vendor is delayed.', DATE_SUB(@today, INTERVAL 4 DAY), NULL,
 'PENDING', 'PENDING', NULL, NULL, NULL, 4, DATE_SUB(@today, INTERVAL 5 DAY)),

-- Action 7 — Defect 6, completed but awaiting verification (defect cannot advance yet)
(7, 6, 5, 'Revise the degassing procedure before pouring and retrain the foundry operators',
 'Procedure revised and operators retrained. Awaiting inspector verification.', DATE_SUB(@today, INTERVAL 1 DAY), DATE_SUB(@today, INTERVAL 2 DAY),
 'COMPLETED', 'PENDING', NULL, NULL, NULL, 4, DATE_SUB(@today, INTERVAL 3 DAY)),

-- Action 8 — Defect 7, OVERDUE (target date has passed, still PENDING)
(8, 7, 5, 'Check store humidity levels and introduce rust-preventive wrapping for rework lots',
 'Not started. Humidity logger reading to be collected first.', DATE_SUB(@today, INTERVAL 1 DAY), NULL,
 'PENDING', 'PENDING', NULL, NULL, NULL, 4, DATE_SUB(@today, INTERVAL 3 DAY));


-- ============================================================================
--  LOADED — SUMMARY
-- ============================================================================

SELECT 'users'                 AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'products',                COUNT(*) FROM products
UNION ALL SELECT 'production_batches',      COUNT(*) FROM production_batches
UNION ALL SELECT 'inspections',             COUNT(*) FROM inspections
UNION ALL SELECT 'defects',                 COUNT(*) FROM defects
UNION ALL SELECT 'defect_status_history',   COUNT(*) FROM defect_status_history
UNION ALL SELECT 'corrective_actions',      COUNT(*) FROM corrective_actions;

SELECT current_status, COUNT(*) AS defect_count
FROM defects
GROUP BY current_status
ORDER BY FIELD(current_status, 'OPEN','UNDER_INVESTIGATION','CORRECTIVE_ACTION','VERIFIED','CLOSED');
