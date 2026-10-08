-- ============================================================================
--  Real-Time Smart Manufacturing Quality Management and Defect Tracking System
-- ============================================================================
--  File     : database/00_create_database.sql
--  Purpose  : Creates the database and the dedicated application user.
--  Run as   : a MySQL administrator (root)
--  Run with : mysql -u root -p < database/00_create_database.sql
--
--  This script is SEPARATE from 01_schema.sql (Phase 7) on purpose:
--    - this file creates the container (database + user + permissions)
--    - Phase 7 creates the seven tables inside it
--  Keeping them apart means you can drop and rebuild the tables without
--  losing your database user and its password.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. THE DATABASE
-- ----------------------------------------------------------------------------
-- utf8mb4 is the correct character set for MySQL 8: it stores the full Unicode
-- range including emoji and Indian-language scripts in four bytes per
-- character. "utf8" in MySQL is a misleading alias for a three-byte subset and
-- should not be used.
--
-- utf8mb4_unicode_ci = case-insensitive comparison, so searching for
-- "Bearing" also matches "bearing".
-- ----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS manufacturing_qms
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

SELECT 'Database manufacturing_qms is ready.' AS Status;


-- ----------------------------------------------------------------------------
-- 2. THE APPLICATION USER
-- ----------------------------------------------------------------------------
-- WHY NOT JUST USE root?
--
--   1. The application only ever needs to read and write its own seven tables.
--      Using root would give a bug or a leaked password the power to drop any
--      database on the server.
--   2. If the password leaks (and student projects do get pushed to a public
--      repo by accident), the damage is limited to this one database.
--   3. It is a genuine answer in the viva to "describe the security of your
--      database connection". "I created a least-privilege user" is a much
--      better answer than "I used root".
--
-- REPLACE THE TOKEN BELOW WITH A PASSWORD OF YOUR OWN before running this
-- script. Use something you do not use anywhere else, and put the same value
-- into backend/src/main/resources/application.properties (which is never
-- committed). This file IS committed, so it must never contain a real
-- credential - the token <<CHANGE_ME>> is deliberately not a usable password,
-- and MySQL will refuse it as written, which is the point.
--
--   mysql -u root -p < database/00_create_database.sql     (will stop on the token)
--
-- ...so edit the line, save, then run it.
-- ----------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'qms_user'@'localhost'
    IDENTIFIED BY '<<CHANGE_ME>>';

-- Grant permissions on THIS database only, not on *.*
--
-- These four are the ONLY privileges the running application needs:
--   SELECT  read data
--   INSERT  create a product, batch, inspection, defect, action
--   UPDATE  edit a record, change a status, deactivate a user
--   DELETE  remove a product that has no batches (FR-03.8)
--
-- Note what is deliberately ABSENT: DROP, CREATE, ALTER and GRANT.
-- The application never changes the structure of the database — the schema
-- is fixed by 01_schema.sql and Hibernate is set to `validate`, not `update`.
-- So the application user cannot drop a table even if the application were
-- compromised or a query went wrong. That is the least-privilege principle,
-- and it is worth mentioning in the viva.
--
-- CONSEQUENCE: run 01_schema.sql and 02_sample_data.sql as root, not as
-- qms_user, because those scripts DROP and CREATE tables.
GRANT SELECT, INSERT, UPDATE, DELETE
    ON manufacturing_qms.* TO 'qms_user'@'localhost';

FLUSH PRIVILEGES;

SELECT 'Application user qms_user created and granted read/write access.' AS Status;


-- ----------------------------------------------------------------------------
-- 3. VERIFY
-- ----------------------------------------------------------------------------
-- Run these three statements yourself to confirm the setup worked.
-- ----------------------------------------------------------------------------
SHOW DATABASES LIKE 'manufacturing_qms';

SELECT user, host FROM mysql.user WHERE user = 'qms_user';

SHOW GRANTS FOR 'qms_user'@'localhost';

-- Expected output of the last statement (approximately):
--   GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
--        ON manufacturing_qms.* TO `qms_user`@`localhost`


-- ----------------------------------------------------------------------------
-- 4. IF YOU NEED TO START OVER
-- ----------------------------------------------------------------------------
-- Uncomment and run these only if you genuinely want to erase everything.
-- ----------------------------------------------------------------------------
-- DROP DATABASE IF EXISTS manufacturing_qms;
-- DROP USER IF EXISTS 'qms_user'@'localhost';


-- ============================================================================
--  NOTES FOR WINDOWS USERS
-- ============================================================================
--  If you run this from the MySQL Command Line Client, run the statements
--  one at a time, or use:   source C:/path/to/00_create_database.sql
--
--  If MySQL refuses the password because of password-strength policy, either
--  choose a stronger one or relax the policy temporarily with:
--      SET GLOBAL validate_password.policy = LOW;
--  (You must restart MySQL after changing validate_password settings.)
-- ============================================================================
