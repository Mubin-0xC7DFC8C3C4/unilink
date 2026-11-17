-- V010 Must-change password + temporary-password expiry + recovery-code hardening
-- (Phase 3 Worker A, D-093..D-097). Additive only: no existing column, table or
-- row is changed, and the DEFAULT fills must_change_password for existing rows.
--  * user_credentials / admin_accounts gain must_change_password (set by a
--    moderator credential reset and by FORCE_RESET) and temp_password_expires_at
--    (24 h from issue, D-095).
--  * admin_recovery_codes.code_hash widens from CHAR(64) (which only fits an
--    unsalted SHA-256 hex digest) to VARCHAR(255) so it can hold an Argon2id
--    hash (D-096), and gains expires_at (90 days from enrolment).
ALTER TABLE user_credentials ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN temp_password_expires_at DATETIME(3) NULL;
ALTER TABLE admin_accounts ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN temp_password_expires_at DATETIME(3) NULL;
ALTER TABLE admin_recovery_codes MODIFY COLUMN code_hash VARCHAR(255) NOT NULL, ADD COLUMN expires_at DATETIME(3) NULL;
