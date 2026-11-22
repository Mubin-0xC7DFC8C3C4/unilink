-- V001 master data (D-020 department_catalog, D-074 six sectors)
CREATE TABLE IF NOT EXISTS universities (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL, short_name VARCHAR(64) NULL,
  email_domains VARCHAR(1024) NULL, active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_universities_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS department_catalog (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, name VARCHAR(255) NOT NULL,
  short_name VARCHAR(64) NULL, active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_catalog_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS departments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  university_id BIGINT UNSIGNED NOT NULL, catalog_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(255) NOT NULL, active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_dept_uni_catalog (university_id, catalog_id),
  CONSTRAINT fk_dept_uni FOREIGN KEY (university_id) REFERENCES universities(id) ON DELETE RESTRICT,
  CONSTRAINT fk_dept_cat FOREIGN KEY (catalog_id) REFERENCES department_catalog(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS batches (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  university_id BIGINT UNSIGNED NOT NULL, department_id BIGINT UNSIGNED NULL,
  label VARCHAR(32) NOT NULL, start_year INT NULL, active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_batch (university_id, department_id, label),
  CONSTRAINT fk_batch_uni FOREIGN KEY (university_id) REFERENCES universities(id) ON DELETE RESTRICT,
  CONSTRAINT fk_batch_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS sector_tags (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, name VARCHAR(128) NOT NULL, active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_sector_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS skills (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, name VARCHAR(128) NOT NULL, active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_skill_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS system_settings (
  setting_key VARCHAR(128) NOT NULL, setting_value VARCHAR(1024) NOT NULL,
  description VARCHAR(512) NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Seed: 6 sectors (D-074), settings defaults (D-034/D-069).
-- Portability (D-167): seed inserts use INSERT IGNORE instead of
-- ON DUPLICATE KEY UPDATE ... VALUES() — the multi-table SELECT form is
-- rejected by MySQL 8.0.46 (error 1064) and VALUES() is unsupported on
-- TiDB. Seeds run once on a fresh database; ignore-on-duplicate preserves
-- the intended first-write-wins behaviour on both engines.
INSERT IGNORE INTO sector_tags (name) VALUES ('Software Engineering'),('Data Science'),('AI/ML'),('Cyber Security'),('Web Development'),('Competitive Programming');
INSERT IGNORE INTO system_settings (setting_key, setting_value, description) VALUES
  ('report.auto_hide_threshold','5','Distinct verified reporters required to auto-hide a post/event/comment (D-034)'),
  ('report.strike_window_days','30','Window for the 3-strikes rule (FR-REP-04)'),
  ('report.strike_count','3','Auto-hides in window that flag a user for review'),
  ('verification.overdue_hours','48','Hours after which a PENDING_REVIEW request raises an overdue alert (D-038)'),
  ('verification.resubmit_max_24h','3','Max resubmissions per rolling 24h (D-012)'),
  ('id_image.retention_days_approved','7','Days to keep ID image after approval (D-053)'),
  ('id_image.retention_days_rejected','30','Days to keep ID image after rejection (D-053)');
INSERT IGNORE INTO universities (name, short_name, email_domains) VALUES
  ('Metropolitan University','MU','mu.edu.bd'),('University of Dhaka','DU','du.ac.bd');
INSERT IGNORE INTO department_catalog (name, short_name) VALUES
  ('Computer Science and Engineering','CSE'),('Electrical and Electronic Engineering','EEE');
INSERT IGNORE INTO departments (university_id, catalog_id, name)
  SELECT u.id, c.id, CONCAT(c.name,' — ',u.short_name) FROM universities u CROSS JOIN department_catalog c;
INSERT IGNORE INTO batches (university_id, department_id, label, start_year)
  SELECT d.university_id, d.id, '45', 2022 FROM departments d;
INSERT IGNORE INTO batches (university_id, department_id, label, start_year)
  SELECT d.university_id, d.id, '44', 2021 FROM departments d;
