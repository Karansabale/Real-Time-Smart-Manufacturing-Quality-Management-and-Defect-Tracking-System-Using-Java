-- ============================================================================
--  Real-Time Smart Manufacturing Quality Management and Defect Tracking System
-- ============================================================================
--  File    : database/01_schema.sql
--  Phase   : 7 — Database Design
--  Purpose : Creates the seven tables that make up the quality management
--            database.
--
-- Run as  : root (or any administrator). NOT as qms_user.
--           qms_user deliberately has no DROP or CREATE privilege — see the
--           explanation in 00_create_database.sql.
-- Run with: mysql -u root -p manufacturing_qms < database/01_schema.sql
--
--  TESTED  : This script was executed against MySQL 8.4.6 on 2026-10-07.
--            All seven tables, 23 foreign keys and 12 CHECK constraints were
--            created without error, and the result was verified by running
--            03_verification_queries.sql.
-- ============================================================================

USE manufacturing_qms;

SET NAMES utf8mb4;


-- ============================================================================
--  WARNING — READ BEFORE RUNNING
-- ============================================================================
--  The DROP statements below DELETE EVERY TABLE AND ALL DATA IN THEM.
--  They are included so that you can rebuild the database cleanly while
--  developing. If you have data you want to keep, COMMENT THESE OUT.
--
--  Order matters: tables are dropped children-first, so that no table is
--  dropped while another table still references it.
-- ============================================================================

DROP TABLE IF EXISTS defect_status_history;
DROP TABLE IF EXISTS corrective_actions;
DROP TABLE IF EXISTS defects;
DROP TABLE IF EXISTS inspections;
DROP TABLE IF EXISTS production_batches;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;


-- ============================================================================
--  TABLE 1 — users
-- ============================================================================
--  People who log in. Three roles (FR-02.2): ADMIN, INSPECTOR, SUPERVISOR.
--
--  The stored role value is INSPECTOR, while the screen label is
--  "Quality Inspector" and SUPERVISOR is shown as "Production Supervisor".
--  The database stores the short machine name; the interface shows the
--  human name. Keep that mapping in one place in the frontend.
--
--  Users are NEVER DELETED (FR-02.9). They are deactivated instead
--  (is_active = FALSE), because inspections and defects reference them and
--  deleting a user would destroy the traceability of who did what.
-- ============================================================================

CREATE TABLE users (
    user_id        INT          NOT NULL AUTO_INCREMENT,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(60)  NOT NULL COMMENT 'BCrypt hash. BCrypt output is always exactly 60 characters.',
    full_name      VARCHAR(100) NOT NULL,
    role           ENUM('ADMIN','INSPECTOR','SUPERVISOR') NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_username (username),
    CONSTRAINT ck_users_full_name CHECK (CHAR_LENGTH(TRIM(full_name)) >= 3),
    CONSTRAINT ck_users_username  CHECK (CHAR_LENGTH(TRIM(username)) >= 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='System users and their role. Never deleted, only deactivated.';


-- ============================================================================
--  TABLE 2 — products
-- ============================================================================
--  The product master list (FR-03). A product with production batches
--  against it cannot be deleted (FR-03.8) — that rule is enforced by the
--  foreign key from production_batches, not by application code alone.
-- ============================================================================

CREATE TABLE products (
    product_id       INT          NOT NULL AUTO_INCREMENT,
    product_code     VARCHAR(20)  NOT NULL COMMENT 'Business identifier quoted on the shop floor, e.g. P-101',
    product_name     VARCHAR(100) NOT NULL,
    category         VARCHAR(50)  NOT NULL COMMENT 'Free text grouping, e.g. Machined Components. Not an ENUM: kept generic on purpose (D9).',
    specification    VARCHAR(255) NULL     COMMENT 'Optional technical specification',
    created_by       INT          NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       INT          NULL,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_products_code (product_code),
    KEY idx_products_name (product_name),
    CONSTRAINT fk_products_created_by FOREIGN KEY (created_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_products_updated_by FOREIGN KEY (updated_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_products_code CHECK (CHAR_LENGTH(TRIM(product_code)) >= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Product master list.';


-- ============================================================================
--  TABLE 3 — production_batches
-- ============================================================================
--  One manufacturing run of one product (FR-04).
--
--  production_status has only THREE values (FR-04.6). A new batch always
--  starts as PLANNED (FR-04.5), which is why the default is set here.
-- ============================================================================

CREATE TABLE production_batches (
    batch_id          INT         NOT NULL AUTO_INCREMENT,
    product_id        INT         NOT NULL,
    batch_number      VARCHAR(30) NOT NULL COMMENT 'Unique business reference, e.g. BATCH-2026-001',
    quantity_produced INT         NOT NULL,
    production_line   VARCHAR(30) NOT NULL COMMENT 'Stored as text, not a master table (D9)',
    start_date        DATE        NOT NULL,
    end_date          DATE        NULL     COMMENT 'Filled in when the batch reaches COMPLETED (FR-04.7)',
    production_status ENUM('PLANNED','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PLANNED',
    created_by        INT         NOT NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        INT         NULL,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_batches_number (batch_number),
    KEY idx_batches_product (product_id),
    KEY idx_batches_status (production_status),
    CONSTRAINT fk_batches_product    FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE RESTRICT,
    CONSTRAINT fk_batches_created_by FOREIGN KEY (created_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_batches_updated_by FOREIGN KEY (updated_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_batches_qty   CHECK (quantity_produced > 0),
    CONSTRAINT ck_batches_dates CHECK (end_date IS NULL OR end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='One manufacturing run of one product.';


-- ============================================================================
--  TABLE 4 — inspections
-- ============================================================================
--  One quality check (FR-05).
--
--  There is NO accepted_quantity column. Accepted quantity is always
--  inspected_qty - rejected_qty, so storing it would allow the three numbers
--  to contradict each other (Phase 5, D3). It is derived in the entity.
--
--  batch_id is NULLABLE: an inspection may be recorded against a product
--  without naming a specific batch (FR-05.1).
-- ============================================================================

CREATE TABLE inspections (
    inspection_id   INT          NOT NULL AUTO_INCREMENT,
    product_id      INT          NOT NULL,
    batch_id        INT          NULL     COMMENT 'Optional: the specific batch inspected',
    inspector_id    INT          NOT NULL COMMENT 'The logged-in user who performed the check (FR-05.7)',
    inspection_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inspection_type ENUM('INCOMING','IN_PROCESS','FINAL') NOT NULL,
    inspected_qty   INT          NOT NULL,
    rejected_qty    INT          NOT NULL DEFAULT 0,
    result          ENUM('PASS','FAIL') NOT NULL,
    remarks         VARCHAR(500) NULL,
    created_by      INT          NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      INT          NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (inspection_id),
    KEY idx_inspections_product (product_id),
    KEY idx_inspections_batch (batch_id),
    KEY idx_inspections_date (inspection_date),
    CONSTRAINT fk_inspections_product    FOREIGN KEY (product_id)   REFERENCES products (product_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inspections_batch      FOREIGN KEY (batch_id)     REFERENCES production_batches (batch_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inspections_inspector  FOREIGN KEY (inspector_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inspections_created_by FOREIGN KEY (created_by)   REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inspections_updated_by FOREIGN KEY (updated_by)   REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_inspections_inspected CHECK (inspected_qty > 0),
    CONSTRAINT ck_inspections_rejected  CHECK (rejected_qty >= 0 AND rejected_qty <= inspected_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='One quality inspection, with quantities and Pass/Fail result.';


-- ============================================================================
--  TABLE 5 — defects
-- ============================================================================
--  One quality problem found (FR-06).
--
--  defect_ref (e.g. DEF-2026-0001) is the reference people quote on the shop
--  floor and in meetings (FR-06.8). It is generated by the service layer, in
--  the form DEF-<year>-<4 digit sequence>.
--
--  current_status is the ONLY status column on this table. The full history
--  of how the defect got here lives in defect_status_history, which is what
--  makes the "no forward skipping" rule (FR-07.5) auditable.
--
--  batch_id and inspection_id are NULLABLE by design: a defect is sometimes
--  reported directly, without a formal inspection (Phase 5, D5).
-- ============================================================================

CREATE TABLE defects (
    defect_id       INT          NOT NULL AUTO_INCREMENT,
    defect_ref      VARCHAR(20)  NOT NULL COMMENT 'Human-readable reference, e.g. DEF-2026-0001 (FR-06.8)',
    product_id      INT          NOT NULL,
    batch_id        INT          NULL,
    inspection_id   INT          NULL     COMMENT 'Set when the defect was found during an inspection (FR-06.2)',
    reported_by     INT          NOT NULL,
    defect_category ENUM('DIMENSIONAL','SURFACE_FINISH','MATERIAL','ASSEMBLY','FUNCTIONAL','PACKAGING','OTHER') NOT NULL,
    description     VARCHAR(500) NOT NULL COMMENT 'Minimum 10 characters, enforced by @Size in the DTO (FR-06.4)',
    severity        ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    units_affected  INT          NOT NULL DEFAULT 1,
    current_status  ENUM('OPEN','UNDER_INVESTIGATION','CORRECTIVE_ACTION','VERIFIED','CLOSED') NOT NULL DEFAULT 'OPEN',
    created_by      INT          NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      INT          NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (defect_id),
    UNIQUE KEY uk_defects_ref (defect_ref),
    KEY idx_defects_status (current_status),
    KEY idx_defects_severity (severity),
    KEY idx_defects_product (product_id),
    KEY idx_defects_batch (batch_id),
    CONSTRAINT fk_defects_product      FOREIGN KEY (product_id)    REFERENCES products (product_id) ON DELETE RESTRICT,
    CONSTRAINT fk_defects_batch        FOREIGN KEY (batch_id)      REFERENCES production_batches (batch_id) ON DELETE RESTRICT,
    CONSTRAINT fk_defects_inspection   FOREIGN KEY (inspection_id) REFERENCES inspections (inspection_id) ON DELETE RESTRICT,
    CONSTRAINT fk_defects_reported_by  FOREIGN KEY (reported_by)   REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_defects_created_by   FOREIGN KEY (created_by)    REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_defects_updated_by   FOREIGN KEY (updated_by)    REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_defects_units CHECK (units_affected > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='A quality defect, with its category, severity and current status.';


-- ============================================================================
--  TABLE 6 — defect_status_history
-- ============================================================================
--  One row for EVERY status change of a defect (FR-07.8), including the
--  very first row, where from_status is NULL and to_status is OPEN.
--
--  This table is APPEND-ONLY. The application provides no update and no
--  delete path for it, so the history cannot be rewritten. That is the whole
--  point: it is the audit trail that proves a defect did not skip a stage.
--
--  from_status is NULLABLE for exactly one reason: the first row of each
--  defect has no previous status. NULL means "this defect was created".
--
--  This is the fastest-growing table in the schema: roughly five or more
--  rows per defect over its lifetime.
-- ============================================================================

CREATE TABLE defect_status_history (
    history_id  INT          NOT NULL AUTO_INCREMENT,
    defect_id   INT          NOT NULL,
    from_status ENUM('OPEN','UNDER_INVESTIGATION','CORRECTIVE_ACTION','VERIFIED','CLOSED') NULL
                             COMMENT 'NULL only on the very first row of a defect',
    to_status   ENUM('OPEN','UNDER_INVESTIGATION','CORRECTIVE_ACTION','VERIFIED','CLOSED') NOT NULL,
    remark      VARCHAR(500) NULL COMMENT 'Reason for the change. MANDATORY when moving a defect backwards (FR-07.6).',
    changed_by  INT          NOT NULL,
    changed_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    KEY idx_history_defect (defect_id, changed_at),
    CONSTRAINT fk_history_defect     FOREIGN KEY (defect_id)  REFERENCES defects (defect_id) ON DELETE RESTRICT,
    CONSTRAINT fk_history_changed_by FOREIGN KEY (changed_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_history_change CHECK (from_status IS NULL OR from_status <> to_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only audit trail: one row per defect status change.';


-- ============================================================================
--  TABLE 7 — corrective_actions
-- ============================================================================
--  Work assigned to fix a defect, plus its verification (FR-08).
--
--  The OVERDUE flag (FR-08.8) is NOT a column. It is derived:
--        target_date < CURDATE() AND progress_status <> 'COMPLETED'
--  Storing it would mean a column that silently goes stale overnight.
--
--  verification_status starts at PENDING. It is set to EFFECTIVE or
--  NOT_EFFECTIVE by an Inspector (FR-08.9). The CHECK constraint below makes
--  it impossible to record an outcome without recording who verified it.
-- ============================================================================

CREATE TABLE corrective_actions (
    action_id           INT          NOT NULL AUTO_INCREMENT,
    defect_id           INT          NOT NULL,
    responsible_person_id INT        NOT NULL COMMENT 'Selected from active users (FR-08.3)',
    action_description  VARCHAR(500) NOT NULL COMMENT 'Minimum 10 characters, enforced by @Size in the DTO (FR-08.2)',
    progress_remark     VARCHAR(500) NULL COMMENT 'Latest progress note (FR-08.6)',
    target_date         DATE         NOT NULL COMMENT 'Must be today or later at creation time (FR-08.4). Cannot be a CHECK constraint because it depends on the current date.',
    completion_date     DATE         NULL     COMMENT 'Set when the work is finished (FR-08.7)',
    progress_status     ENUM('PENDING','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PENDING',
    verification_status ENUM('PENDING','EFFECTIVE','NOT_EFFECTIVE') NOT NULL DEFAULT 'PENDING',
    verification_remark VARCHAR(500) NULL,
    verified_by         INT          NULL     COMMENT 'NULL until verification happens (Phase 5, D6)',
    verified_at         DATETIME     NULL,
    created_by          INT          NOT NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          INT          NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (action_id),
    KEY idx_actions_defect (defect_id),
    KEY idx_actions_responsible (responsible_person_id),
    KEY idx_actions_overdue (target_date, progress_status),
    CONSTRAINT fk_actions_defect      FOREIGN KEY (defect_id)             REFERENCES defects (defect_id) ON DELETE RESTRICT,
    CONSTRAINT fk_actions_responsible FOREIGN KEY (responsible_person_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_actions_verified_by FOREIGN KEY (verified_by)           REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_actions_created_by  FOREIGN KEY (created_by)            REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_actions_updated_by  FOREIGN KEY (updated_by)            REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_actions_description CHECK (CHAR_LENGTH(TRIM(action_description)) >= 10),
    CONSTRAINT ck_actions_verified    CHECK (verification_status = 'PENDING' OR verified_by IS NOT NULL),
    CONSTRAINT ck_actions_completion  CHECK (completion_date IS NULL OR progress_status = 'COMPLETED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Corrective action assigned to fix a defect, plus its verification.';


-- ============================================================================
--  VERIFY THE SCHEMA WAS CREATED
-- ============================================================================

SELECT TABLE_NAME, TABLE_ROWS, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'manufacturing_qms'
ORDER BY TABLE_NAME;

SELECT COUNT(*) AS foreign_key_count
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'manufacturing_qms'
  AND CONSTRAINT_TYPE = 'FOREIGN KEY';

SELECT COUNT(*) AS check_constraint_count
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'manufacturing_qms'
  AND CONSTRAINT_TYPE = 'CHECK';


-- ============================================================================
--  DESIGN NOTES — the questions you will be asked about this schema
-- ============================================================================
--
--  Q: Why is there no ON DELETE CASCADE anywhere?
--  A: Because a quality record must never disappear silently. Deleting a
--     product that has batches is blocked by the database (FR-03.8), deleting
--     a user is blocked entirely (FR-02.9), and deleting a defect does not
--     take its history with it. Every foreign key is RESTRICT. If you can
--     delete a parent row, something is wrong with the design, not with the
--     data.
--
--  Q: Why ENUM and not a lookup table?
--  A: The values are fixed at design time and users never edit them. Lookup
--     tables would add five tables and five joins to every query for no
--     functional gain (Phase 5, D2). The accepted trade-off: adding a new
--     severity later needs a schema change, and that is documented under
--     Future Scope.
--
--  Q: Why is the overdue flag not a column?
--  A: A stored flag goes stale the moment the clock passes midnight. Deriving
--     it from target_date and progress_status is always correct, and the
--     index idx_actions_overdue makes the query fast (Phase 5, D3).
--
--  Q: Why two status columns on corrective_actions?
--  A: They answer different questions. progress_status is "is the work done?"
--     (the Supervisor's question). verification_status is "did the fix
--     actually work?" (the Inspector's question, FR-08.9). A completed action
--     can still fail verification, and when it does the defect must not be
--     closed (FR-08.10).
--
--  Q: Why is defect_status_history a separate table instead of a column?
--  A: FR-07.8 requires the complete history of who changed a defect, when and
--     why. One column can only hold the latest value. A separate append-only
--     table holds all of them and cannot be quietly rewritten.
-- ============================================================================
