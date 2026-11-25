-- V017: phone change with OTP re-verification of the new number (D-199).
-- ADDITIVE ONLY: two NULLABLE columns on users, nothing dropped, renamed
-- or overwritten, and no existing row is changed (legacy rows keep
-- pending_phone / pending_phone_requested_at NULL).
-- TiDB rules (D-188): ONE schema change per statement. Never combine two
-- ADD COLUMNs in a single ALTER (TiDB Error 1072).
-- Re-run property (Flyway sense): Flyway records V017 as applied and never
-- re-runs it; a manual re-run of this file against a database where the
-- columns already exist fails on the first ALTER (duplicate column), so a
-- re-run must be guarded by a column-existence check, exactly as for V014–
-- V016. The columns are NULLABLE, so applying V017 to a database with
-- existing users changes no stored value.

-- D-199: the new number awaiting OTP verification. The old verified phone
-- stays in force until the new number verifies.
ALTER TABLE users ADD COLUMN pending_phone VARCHAR(32) NULL;

-- D-199: when the pending change was requested (audit trail for the
-- pending state; the OTP row itself carries the 5-minute expiry).
ALTER TABLE users ADD COLUMN pending_phone_requested_at DATETIME(3) NULL;
