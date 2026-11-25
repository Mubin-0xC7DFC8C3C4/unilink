-- V019: GitHub Pin to Profile columns + OAuth state nonces (D-203).
-- ADDITIVE ONLY: two nullable columns on projects, one new table, nothing
-- dropped, renamed or overwritten, and no existing row is changed.
-- TiDB rules (D-188): ONE schema change per statement — each ALTER is its
-- own statement, and the CREATE TABLE is its own statement.
--
-- Pin to Profile: a project row links to a GitHub repository via
-- github_owner / github_repo (both NULL = not pinned). The pin endpoint
-- validates the repo against the owner's live public repo list, so the
-- columns are plain nullable text with no FK to GitHub.
--
-- OAuth state nonces: the HMAC-signed state (userId.nonce.issuedAt) gains
-- expiry + single-use/replay protection (D-203). The nonce row is written
-- at authorize time and consumed (used_at set) at callback time; a replayed
-- or expired state finds no usable row. Expired rows are deleted lazily on
-- consume. A nonce belongs to one user; deleting the user cascades.

-- D-203: pin a project row to a GitHub repository (owner part).
ALTER TABLE projects ADD COLUMN github_owner VARCHAR(255) NULL;

-- D-203: pin a project row to a GitHub repository (repo part).
ALTER TABLE projects ADD COLUMN github_repo VARCHAR(255) NULL;

-- D-203: one-time OAuth state nonces for the GitHub authorize flow.
CREATE TABLE IF NOT EXISTS github_oauth_states (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  nonce VARCHAR(128) NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  used_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_gos_nonce (nonce),
  INDEX idx_gos_expires (expires_at),
  CONSTRAINT fk_gos_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
