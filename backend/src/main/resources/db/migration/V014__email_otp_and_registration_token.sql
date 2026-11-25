-- V014 email OTP codes + registration tokens (D-174/D-175, owner brief 2026-11-25)
-- Email OTP is a 6-digit code (not the 24 h link) for new registrations:
-- email_otps mirrors phone_otps (SHA-256 hash, attempts, single-use).
-- verification_requests gains the registration token (hash only) that
-- scopes the status / email-OTP endpoints without a session or userId.
CREATE TABLE IF NOT EXISTS email_otps (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  otp_hash CHAR(64) NOT NULL, attempts INT NOT NULL DEFAULT 0, used TINYINT(1) NOT NULL DEFAULT 0,
  expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), INDEX idx_emailotp_user (user_id, created_at),
  CONSTRAINT fk_emailotp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- D-188 (2026-11-25): TiDB rejects adding a column and an index on that
-- column in a single ALTER TABLE (Error 1072, "column does not exist") —
-- the first auto-deploy (dep-dc3aps60tbcc73d32t70) failed on exactly this
-- statement. MySQL accepts the single statement (verified on MySQL 8.0.46),
-- so the failure is TiDB-specific. Split into single-change statements:
-- one column per ALTER, index after. TiDB before 6.2 also rejects adding
-- multiple columns in one ALTER ("Unsupported multi schema change"), and
-- the production cluster's TiDB version is not confirmed, so no ALTER
-- here performs more than one schema change.
ALTER TABLE verification_requests ADD COLUMN verification_token_hash CHAR(64) NULL;
ALTER TABLE verification_requests ADD COLUMN verification_token_expires_at DATETIME(3) NULL;
CREATE INDEX idx_verif_token ON verification_requests (verification_token_hash);
