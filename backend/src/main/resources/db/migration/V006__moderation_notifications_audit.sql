-- V006 moderation (D-033/D-034/D-035), notifications (D-029c), announcements (D-039), audit (D-037)
CREATE TABLE IF NOT EXISTS reports (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, reporter_id BIGINT UNSIGNED NOT NULL,
  target_type VARCHAR(32) NOT NULL, target_id BIGINT UNSIGNED NOT NULL,
  reason VARCHAR(1024) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_at DATETIME(3) NULL, resolved_by BIGINT UNSIGNED NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_active (reporter_id, target_type, target_id, status),
  INDEX idx_report_target (target_type, target_id, created_at),
  CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS appeals (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, appellant_id BIGINT UNSIGNED NULL,
  target_type VARCHAR(32) NOT NULL, target_id BIGINT UNSIGNED NOT NULL,
  reason VARCHAR(2000) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  token_hash CHAR(64) NULL, token_expires_at DATETIME(3) NULL,
  decided_by BIGINT UNSIGNED NULL, decision_reason VARCHAR(1024) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), decided_at DATETIME(3) NULL,
  PRIMARY KEY (id), INDEX idx_appeal_target (target_type, target_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS moderation_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, target_type VARCHAR(32) NOT NULL,
  target_id BIGINT UNSIGNED NOT NULL, event_type VARCHAR(32) NOT NULL,
  actor_admin_id BIGINT UNSIGNED NULL, reason VARCHAR(1024) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), INDEX idx_modev_target (target_type, target_id, created_at),
  INDEX idx_modev_type (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS moderation_snapshots (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, report_id BIGINT UNSIGNED NOT NULL,
  message_id BIGINT UNSIGNED NOT NULL, sender_id BIGINT UNSIGNED NULL,
  body MEDIUMTEXT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), INDEX idx_snapshot_report (report_id),
  CONSTRAINT fk_snap_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, recipient_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(64) NOT NULL, target_type VARCHAR(64) NULL, target_id BIGINT UNSIGNED NULL,
  title VARCHAR(255) NOT NULL, body VARCHAR(1024) NULL,
  idempotency_key VARCHAR(255) NOT NULL, read_flag TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_notif_idem (idempotency_key),
  INDEX idx_notif_recipient (recipient_id, created_at),
  CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS notification_preferences (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL, type VARCHAR(64) NOT NULL,
  email_enabled TINYINT(1) NOT NULL DEFAULT 1, in_app_enabled TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id), UNIQUE KEY uk_notifpref (user_id, type),
  CONSTRAINT fk_notifpref_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS announcements (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, admin_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(255) NOT NULL, body MEDIUMTEXT NOT NULL,
  scope VARCHAR(32) NOT NULL DEFAULT 'GLOBAL', university_id BIGINT UNSIGNED NULL,
  expires_at DATETIME(3) NULL, active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), INDEX idx_ann_scope (scope, university_id, created_at),
  CONSTRAINT fk_ann_admin FOREIGN KEY (admin_id) REFERENCES admin_accounts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, actor_admin_id BIGINT UNSIGNED NULL,
  action VARCHAR(128) NOT NULL, target_type VARCHAR(64) NOT NULL, target_id BIGINT UNSIGNED NULL,
  reason VARCHAR(1024) NULL, metadata JSON NULL, ip VARCHAR(64) NULL,
  prev_hash CHAR(64) NOT NULL DEFAULT '', row_hash CHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), INDEX idx_audit_actor (actor_admin_id, created_at),
  INDEX idx_audit_target (target_type, target_id, created_at),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_admin_id) REFERENCES admin_accounts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- D-037 audit immutability: originally two BEFORE UPDATE/DELETE triggers
-- that SIGNALled an error (defence-in-depth). Removed for portability
-- (D-167): TiDB does not support triggers. Append-only is enforced by the
-- application — AuditService only inserts and AuditLogRepository exposes
-- no update or delete operation.
