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
ALTER TABLE verification_requests
  ADD COLUMN verification_token_hash CHAR(64) NULL,
  ADD COLUMN verification_token_expires_at DATETIME(3) NULL,
  ADD INDEX idx_verif_token (verification_token_hash);
