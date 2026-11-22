-- Database-backed file storage (D-168): when app.storage.provider=db,
-- uploaded files live in this table instead of the local filesystem, so
-- uploads survive on hosts with an ephemeral filesystem (e.g. Render
-- Free). Portable DDL: no triggers, no FULLTEXT, no vendor collation —
-- applies identically on MySQL 8 and TiDB.
CREATE TABLE stored_files (
  storage_key VARCHAR(512) NOT NULL,
  namespace VARCHAR(64) NOT NULL,
  content_type VARCHAR(128) NULL,
  size_bytes BIGINT NOT NULL,
  data LONGBLOB NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (storage_key),
  INDEX idx_stored_files_namespace (namespace, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
