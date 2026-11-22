-- V012 full-stack integration (2026-11-22 master build):
-- admin-managed events (DRAFT/PUBLISHED/COMPLETED/CANCELLED lifecycle),
-- threaded comments, ID card back image, GitHub connections, project discussions.
-- Additive only; V001–V011 untouched.

-- Admin-authored events have no student author; the creating admin is
-- recorded on event_details.created_by_admin_id instead.
ALTER TABLE posts MODIFY author_id BIGINT UNSIGNED NULL;

-- Threaded comments: a reply points at its parent comment (same post).
ALTER TABLE comments ADD COLUMN parent_id BIGINT UNSIGNED NULL;
CREATE INDEX idx_comment_parent ON comments (parent_id);
ALTER TABLE comments ADD CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE;

-- Full admin event model on the existing 1:1 event_details table.
ALTER TABLE event_details
  ADD COLUMN organizer_name VARCHAR(255) NULL,
  ADD COLUMN organizing_unit VARCHAR(255) NULL,
  ADD COLUMN contact_info VARCHAR(255) NULL,
  ADD COLUMN short_description VARCHAR(500) NULL,
  ADD COLUMN end_at DATETIME(3) NULL,
  ADD COLUMN timezone VARCHAR(64) NULL DEFAULT 'Asia/Dhaka',
  ADD COLUMN event_type VARCHAR(32) NULL,
  ADD COLUMN venue_name VARCHAR(255) NULL,
  ADD COLUMN full_address VARCHAR(500) NULL,
  ADD COLUMN meeting_link VARCHAR(1024) NULL,
  ADD COLUMN registration_required TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN registration_deadline DATETIME(3) NULL,
  ADD COLUMN max_participants INT NULL,
  ADD COLUMN event_status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  ADD COLUMN created_by_admin_id BIGINT UNSIGNED NULL,
  ADD COLUMN cover_key VARCHAR(255) NULL;
CREATE INDEX idx_event_status ON event_details (event_status, deadline);

-- One registration row per student per event (the service upserts;
-- this constraint makes duplicates impossible at the database level).
ALTER TABLE event_responses ADD UNIQUE KEY uq_event_response (user_id, post_id);

-- ID card back image (front stays in id_card_key); stored privately.
ALTER TABLE users ADD COLUMN id_card_back_key VARCHAR(255) NULL;

-- GitHub connection per student; access tokens are stored AES-GCM
-- encrypted (AesGcm, ENCRYPTION_KEY), never in plaintext.
CREATE TABLE IF NOT EXISTS github_connections (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  github_user_id BIGINT NULL,
  username VARCHAR(255) NOT NULL,
  access_token_enc TEXT NOT NULL,
  scopes VARCHAR(255) NULL,
  connected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_github_user (user_id),
  CONSTRAINT fk_github_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- UniLink-native project discussions (separate from GitHub Issues).
-- Replies are rows with parent_id set; file_ref holds an optional
-- @file path inside the repository.
CREATE TABLE IF NOT EXISTS project_discussions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  repo_full_name VARCHAR(255) NOT NULL,
  author_id BIGINT UNSIGNED NOT NULL,
  parent_id BIGINT UNSIGNED NULL,
  body TEXT NOT NULL,
  file_ref VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  INDEX idx_discussion_repo (repo_full_name, created_at),
  CONSTRAINT fk_discussion_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_discussion_parent FOREIGN KEY (parent_id) REFERENCES project_discussions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
