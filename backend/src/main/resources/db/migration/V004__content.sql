-- V004 content (D-021 visibility, D-023 events, D-025 mentions, D-027 normalised skills)
CREATE TABLE IF NOT EXISTS posts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, author_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(16) NOT NULL DEFAULT 'POST',
  visibility VARCHAR(32) NOT NULL DEFAULT 'UNIVERSITY',
  scope_university_id BIGINT UNSIGNED NULL, scope_department_id BIGINT UNSIGNED NULL,
  scope_catalog_id BIGINT UNSIGNED NULL,
  title VARCHAR(255) NULL, body MEDIUMTEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  INDEX idx_feed_uni (visibility, scope_university_id, scope_department_id, status, created_at, id),
  INDEX idx_feed_hub (visibility, scope_catalog_id, status, created_at, id),
  INDEX idx_post_author (author_id, created_at),
  FULLTEXT KEY ft_posts (title, body),
  CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_scope_uni FOREIGN KEY (scope_university_id) REFERENCES universities(id) ON DELETE RESTRICT,
  CONSTRAINT fk_post_scope_dept FOREIGN KEY (scope_department_id) REFERENCES departments(id) ON DELETE RESTRICT,
  CONSTRAINT fk_post_scope_cat FOREIGN KEY (scope_catalog_id) REFERENCES department_catalog(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS event_details (
  post_id BIGINT UNSIGNED NOT NULL, deadline DATETIME(3) NOT NULL,
  reg_link VARCHAR(1024) NULL, location VARCHAR(255) NULL,
  registration_closed TINYINT(1) NOT NULL DEFAULT 0, archived_at DATETIME(3) NULL,
  PRIMARY KEY (post_id),
  CONSTRAINT fk_event_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_event_deadline ON event_details (deadline, archived_at);
CREATE TABLE IF NOT EXISTS comments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, post_id BIGINT UNSIGNED NOT NULL,
  author_id BIGINT UNSIGNED NOT NULL, body MEDIUMTEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), INDEX idx_comment_post (post_id, created_at),
  CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS comment_mentions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, comment_id BIGINT UNSIGNED NOT NULL,
  mentioned_user_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_mention (comment_id, mentioned_user_id),
  CONSTRAINT fk_mention_comment FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
  CONSTRAINT fk_mention_user FOREIGN KEY (mentioned_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS reactions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  target_type VARCHAR(32) NOT NULL, target_id BIGINT UNSIGNED NOT NULL,
  reaction VARCHAR(32) NOT NULL DEFAULT 'LIKE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_reaction (user_id, target_type, target_id),
  CONSTRAINT fk_reaction_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS attachments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, owner_type VARCHAR(32) NOT NULL,
  owner_id BIGINT UNSIGNED NOT NULL, storage_key VARCHAR(512) NOT NULL,
  file_name VARCHAR(255) NOT NULL, content_type VARCHAR(128) NOT NULL, size_bytes BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), INDEX idx_attach_owner (owner_type, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS post_sector_tags (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, post_id BIGINT UNSIGNED NOT NULL,
  sector_tag_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_post_sector (post_id, sector_tag_id),
  CONSTRAINT fk_pst_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_pst_sector FOREIGN KEY (sector_tag_id) REFERENCES sector_tags(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS bookmarks (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  target_type VARCHAR(32) NOT NULL, target_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_bookmark (user_id, target_type, target_id),
  CONSTRAINT fk_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS event_responses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  post_id BIGINT UNSIGNED NOT NULL, response VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_event_resp (user_id, post_id),
  CONSTRAINT fk_evresp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_evresp_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- FULLTEXT on users.full_name for search (D-028)
CREATE FULLTEXT INDEX ft_users_name ON users (full_name);
