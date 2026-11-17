-- V011: Worker D (Phase 3) data-integrity fixes. Additive only: no existing
-- migration is rewritten, no column/table is dropped, no row is deleted.
-- (Worker A owns V010; this file is V011.)
--
-- A. MessageAttachment.createdAt (Phase 2 verification note): the entity's
--    @PrePersist was empty and there was no created_at column at all, so the
--    DTO always carried null. The sibling Attachment entity (V004) already
--    has the column + @PrePersist, so this brings message_attachments in line.
ALTER TABLE message_attachments ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
--
-- M8 (Profile.links): the column is JSON but the entity is String @Lob and
-- the UI treats links as free display text (a plain URL string is not valid
-- JSON, so MySQL would reject the write at runtime). AuditLog.metadata stays
-- JSON: AuditService always writes ObjectMapper JSON, so it is valid by
-- construction. Links becomes TEXT to match the entity and the UI.
ALTER TABLE profiles MODIFY COLUMN links TEXT NULL;
--
-- M6 (missing FKs): appeals and moderation_events had no FKs at all, and
-- team_members.event_post_id had none (its team_id/user_id FKs exist).
-- Polymorphic targets (appeals.target_id, moderation_events.target_id,
-- reports.target_id) intentionally keep no FK: the target may be a post,
-- comment, user or message. moderation_snapshots.message_id/sender_id
-- intentionally keep no FK either: the snapshot is evidence (D-033) and must
-- survive deletion of the reported message.
ALTER TABLE appeals
  ADD CONSTRAINT fk_appeal_appellant FOREIGN KEY (appellant_id) REFERENCES users(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_appeal_decided_by FOREIGN KEY (decided_by) REFERENCES admin_accounts(id) ON DELETE RESTRICT;
ALTER TABLE moderation_events
  ADD CONSTRAINT fk_modev_actor FOREIGN KEY (actor_admin_id) REFERENCES admin_accounts(id) ON DELETE RESTRICT;
ALTER TABLE team_members
  ADD CONSTRAINT fk_tm_event_post FOREIGN KEY (event_post_id) REFERENCES posts(id) ON DELETE CASCADE;
