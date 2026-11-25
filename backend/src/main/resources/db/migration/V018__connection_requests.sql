-- V018: mutual connection requests (D-200).
-- ADDITIVE ONLY: one new table + two indexes, nothing dropped, renamed
-- or overwritten, and no existing row is changed.
-- Modelled on team_join_requests (V005): requester/addressee, status,
-- message, created/decided timestamps. A connection is an ACCEPTED row,
-- readable in either direction; removal transitions it to REMOVED (the
-- audit row is kept, and a fresh request is allowed afterwards).
-- The UNIQUE (requester_id, addressee_id, status) key blocks duplicate
-- PENDING rows while allowing a fresh request after REJECTED/CANCELLED/
-- REMOVED (a different status value). Self-request is enforced in
-- service (no reliance on CHECK constraints on TiDB).
-- TiDB rules (D-188): ONE schema change per statement. The CREATE TABLE
-- is one statement; each CREATE INDEX is its own statement.

-- D-200: the connection-request table. Statuses: PENDING, ACCEPTED,
-- REJECTED, CANCELLED, REMOVED.
CREATE TABLE IF NOT EXISTS connection_requests (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  requester_id BIGINT UNSIGNED NOT NULL,
  addressee_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  message VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  decided_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_conn_req (requester_id, addressee_id, status),
  CONSTRAINT fk_cr_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_cr_addressee FOREIGN KEY (addressee_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- D-200: incoming-request lookup (addressee's pending inbox).
CREATE INDEX idx_cr_addressee_pending ON connection_requests (addressee_id, status);

-- D-200: outgoing-request lookup (requester's pending list).
CREATE INDEX idx_cr_requester_pending ON connection_requests (requester_id, status);
