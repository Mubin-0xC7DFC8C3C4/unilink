-- V008 Add created_at columns the entities expect (audit M1/M2).
-- VerificationRequest.createdAt and NotificationPreference.createdAt map to
-- created_at, but V002/V006 never created the column, so any read/write of
-- those rows would fail at runtime with ddl-auto: none. Additive only: no
-- existing column, table or row is changed, and the DEFAULT fills the column
-- for any rows that already exist.
ALTER TABLE verification_requests ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
ALTER TABLE notification_preferences ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
