-- V016: department catalogue (D-198) — canonical disciplines + aliases,
-- verified links only.
-- ADDITIVE ONLY: one new table, three NULLABLE provenance columns, one
-- index, seeds. Nothing is dropped, renamed or overwritten, and no
-- existing row is changed (the four MU/DU link rows below already exist
-- from V001; INSERT IGNORE makes re-asserting them a no-op).
-- TiDB rules (D-188): ONE schema change per statement. Never combine an
-- ADD COLUMN with an ADD INDEX in a single ALTER (TiDB Error 1072).
-- Seeds use INSERT IGNORE (D-167): re-runnable; never ON DUPLICATE KEY
-- UPDATE (rejected by MySQL 8.0.46 error 1064, unsupported on TiDB).
-- The V001 CROSS JOIN seed pattern is explicitly NOT repeated: a bulk
-- cross-join would invent university->department mappings that were
-- never verified. Link rows are seeded ONLY for links verified from the
-- university's own official source (docs/department-catalogue-research.md
-- §4): MU/DU x CSE/EEE. Every other university keeps the typed-department
-- fallback (D-193), unchanged.

-- D-198: aliases, globally unique so e.g. "CSE" maps to exactly one
-- canonical row. catalog_id RESTRICT: a catalog row with aliases or
-- department links cannot be deleted out from under them.
CREATE TABLE IF NOT EXISTS department_aliases (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  catalog_id BIGINT UNSIGNED NOT NULL,
  alias VARCHAR(128) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dept_alias (alias),
  CONSTRAINT fk_deptalias_catalog FOREIGN KEY (catalog_id) REFERENCES department_catalog(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- D-198: provenance on department_catalog, one column per statement.
-- All NULLABLE: the V016 seed leaves them NULL (the canonical names come
-- from the brief's list, which is not a URL — see the research doc §2);
-- a Platform Admin records a confirming official source when verifying
-- a row or link through the master-data flow.
ALTER TABLE department_catalog ADD COLUMN source_url VARCHAR(512) NULL;
ALTER TABLE department_catalog ADD COLUMN verified_at DATETIME(3) NULL;
ALTER TABLE department_catalog ADD COLUMN verified_by VARCHAR(128) NULL;

-- Index on the alias lookup column, as its own statement (never combined
-- with an ALTER that adds a column — TiDB Error 1072, D-188).
CREATE INDEX idx_deptalias_catalog ON department_aliases (catalog_id);

-- D-198: canonical disciplines (32 rows: the 2 V001 rows + 30 new).
-- Source: the brief's recognised-discipline list; full table with
-- short_name + aliases in docs/department-catalogue-research.md §3.
-- MBBS/BDS/Nursing are NOT seeded: no university source confirmed them
-- in this session (research doc §3/§5).
INSERT IGNORE INTO department_catalog (name, short_name) VALUES
  ('Software Engineering', 'SWE'),
  ('Civil Engineering', 'Civil'),
  ('Mechanical Engineering', 'Mechanical'),
  ('Industrial and Production Engineering', 'IPE'),
  ('Architecture', 'Architecture'),
  ('Pharmacy', 'Pharmacy'),
  ('Biotechnology', 'Biotech'),
  ('Genetic Engineering', 'Genetic Engg'),
  ('Microbiology', 'Microbiology'),
  ('Chemistry', 'Chemistry'),
  ('Physics', 'Physics'),
  ('Mathematics', 'Maths'),
  ('Statistics', 'Statistics'),
  ('Economics', 'Economics'),
  ('Business Administration', 'BBA'),
  ('Accounting', 'Accounting'),
  ('Finance', 'Finance'),
  ('Marketing', 'Marketing'),
  ('Management', 'Management'),
  ('English', 'English'),
  ('Law', 'Law'),
  ('Sociology', 'Sociology'),
  ('Political Science', 'Poli Sci'),
  ('Psychology', 'Psychology'),
  ('Journalism & Media Studies', 'Journalism'),
  ('Public Administration', 'Pub Admin'),
  ('Environmental Science', 'Env Science'),
  ('Agriculture', 'Agriculture'),
  ('Fisheries', 'Fisheries'),
  ('Veterinary Science', 'Vet Science');

-- D-198: aliases, one INSERT...SELECT batch per canonical row (V001/D-167
-- pattern: resolve catalog_id by name at insert time, never hardcode ids).
-- INSERT IGNORE: re-runnable; the unique key on alias keeps each alias
-- mapped to exactly one canonical row.
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Computer Science' FROM department_catalog WHERE name = 'Computer Science and Engineering'
UNION ALL SELECT id, 'Computer Science & Engineering' FROM department_catalog WHERE name = 'Computer Science and Engineering';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Software Engg' FROM department_catalog WHERE name = 'Software Engineering'
UNION ALL SELECT id, 'Software Eng.' FROM department_catalog WHERE name = 'Software Engineering';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Electrical Engineering' FROM department_catalog WHERE name = 'Electrical and Electronic Engineering'
UNION ALL SELECT id, 'Electronic Engineering' FROM department_catalog WHERE name = 'Electrical and Electronic Engineering';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Civil Engg' FROM department_catalog WHERE name = 'Civil Engineering';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Mech Engg' FROM department_catalog WHERE name = 'Mechanical Engineering'
UNION ALL SELECT id, 'ME' FROM department_catalog WHERE name = 'Mechanical Engineering';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Industrial Engineering' FROM department_catalog WHERE name = 'Industrial and Production Engineering'
UNION ALL SELECT id, 'Production Engineering' FROM department_catalog WHERE name = 'Industrial and Production Engineering';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Arch' FROM department_catalog WHERE name = 'Architecture';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Pharm' FROM department_catalog WHERE name = 'Pharmacy';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Biotechnology Engg' FROM department_catalog WHERE name = 'Biotechnology';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Genetic Eng.' FROM department_catalog WHERE name = 'Genetic Engineering';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Microbio' FROM department_catalog WHERE name = 'Microbiology';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Chem' FROM department_catalog WHERE name = 'Chemistry';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Phy' FROM department_catalog WHERE name = 'Physics';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Math' FROM department_catalog WHERE name = 'Mathematics';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Stats' FROM department_catalog WHERE name = 'Statistics'
UNION ALL SELECT id, 'Stat' FROM department_catalog WHERE name = 'Statistics';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Econ' FROM department_catalog WHERE name = 'Economics';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Business Admin' FROM department_catalog WHERE name = 'Business Administration';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Acc' FROM department_catalog WHERE name = 'Accounting';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Fin' FROM department_catalog WHERE name = 'Finance';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Mkt' FROM department_catalog WHERE name = 'Marketing';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Mgmt' FROM department_catalog WHERE name = 'Management';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Eng' FROM department_catalog WHERE name = 'English';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'LLB' FROM department_catalog WHERE name = 'Law';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Soc' FROM department_catalog WHERE name = 'Sociology';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Political Sci' FROM department_catalog WHERE name = 'Political Science'
UNION ALL SELECT id, 'PS' FROM department_catalog WHERE name = 'Political Science';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Psych' FROM department_catalog WHERE name = 'Psychology';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Media Studies' FROM department_catalog WHERE name = 'Journalism & Media Studies'
UNION ALL SELECT id, 'JMS' FROM department_catalog WHERE name = 'Journalism & Media Studies';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Public Admin' FROM department_catalog WHERE name = 'Public Administration'
UNION ALL SELECT id, 'PA' FROM department_catalog WHERE name = 'Public Administration';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Environmental Sci' FROM department_catalog WHERE name = 'Environmental Science'
UNION ALL SELECT id, 'ES' FROM department_catalog WHERE name = 'Environmental Science';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Agri' FROM department_catalog WHERE name = 'Agriculture';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Fish' FROM department_catalog WHERE name = 'Fisheries';
INSERT IGNORE INTO department_aliases (catalog_id, alias)
SELECT id, 'Veterinary Sci' FROM department_catalog WHERE name = 'Veterinary Science'
UNION ALL SELECT id, 'DVM' FROM department_catalog WHERE name = 'Veterinary Science';

-- D-198: verified links ONLY, re-asserted idempotently (these four rows
-- already exist from V001; INSERT IGNORE + uk_dept_uni_catalog makes this
-- a no-op on a fresh database). Explicit university + catalog WHERE
-- clauses — never a cross-join. No other link row is seeded: no other
-- university->department link was verified from that university's own
-- official source in this session (research doc §5).
INSERT IGNORE INTO departments (university_id, catalog_id, name)
SELECT u.id, c.id, CONCAT(c.name, ' — ', u.short_name) FROM universities u JOIN department_catalog c ON c.name = 'Computer Science and Engineering' WHERE u.name = 'Metropolitan University';
INSERT IGNORE INTO departments (university_id, catalog_id, name)
SELECT u.id, c.id, CONCAT(c.name, ' — ', u.short_name) FROM universities u JOIN department_catalog c ON c.name = 'Electrical and Electronic Engineering' WHERE u.name = 'Metropolitan University';
INSERT IGNORE INTO departments (university_id, catalog_id, name)
SELECT u.id, c.id, CONCAT(c.name, ' — ', u.short_name) FROM universities u JOIN department_catalog c ON c.name = 'Computer Science and Engineering' WHERE u.name = 'University of Dhaka';
INSERT IGNORE INTO departments (university_id, catalog_id, name)
SELECT u.id, c.id, CONCAT(c.name, ' — ', u.short_name) FROM universities u JOIN department_catalog c ON c.name = 'Electrical and Electronic Engineering' WHERE u.name = 'University of Dhaka';
