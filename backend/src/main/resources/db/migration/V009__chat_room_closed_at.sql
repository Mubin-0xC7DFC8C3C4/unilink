-- V009: temporary-room close support (FR-MSG-08, I-7).
-- Additive only: one nullable column on chat_rooms. A closed room rejects
-- new messages in MessagingService; its history stays readable.
ALTER TABLE chat_rooms ADD COLUMN closed_at DATETIME(3) NULL;
