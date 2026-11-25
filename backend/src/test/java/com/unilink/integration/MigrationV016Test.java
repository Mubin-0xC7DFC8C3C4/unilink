package com.unilink.integration;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Static regression tests for V016 (D-198): the migration is MySQL/TiDB
 *  DDL that H2 cannot run, so — following the MigrationV014Test /
 *  MigrationV015Test pattern — this asserts the facts that together close
 *  the gap. The chain itself must still be verified by a genuine Flyway
 *  run against real MySQL on a scratch database (see the D-188
 *  procedure): H2-only verification is not enough. */
class MigrationV016Test {

  @Test void v016AddsAtMostOneColumnPerAlter() throws Exception {
    String v016 = migrationSql();
    // TiDB before 6.2 rejects adding multiple columns in one ALTER, and
    // the production cluster's TiDB version is not confirmed.
    for (String stmt : v016.split(";")) {
      if (stmt.contains("ALTER TABLE")) {
        int adds = stmt.split("ADD COLUMN", -1).length - 1;
        assertTrue(adds <= 1,
            "V016 must add at most one column per ALTER (TiDB multi-schema restriction); statement was: " + stmt.trim());
        assertFalse(stmt.contains("ADD INDEX"),
            "V016 must not ADD INDEX in the same ALTER that adds a column (TiDB Error 1072); statement was: " + stmt.trim());
      }
    }
  }

  @Test void v016AddsProvenanceColumnsNullable() throws Exception {
    String v016 = migrationSql();
    assertTrue(v016.contains("ADD COLUMN source_url VARCHAR(512) NULL"),
        "V016 must add department_catalog.source_url (D-198)");
    assertTrue(v016.contains("ADD COLUMN verified_at DATETIME(3) NULL"),
        "V016 must add department_catalog.verified_at (D-198)");
    assertTrue(v016.contains("ADD COLUMN verified_by VARCHAR(128) NULL"),
        "V016 must add department_catalog.verified_by (D-198)");
    // Every added column is NULLABLE (the CREATE TABLE for the new
    // department_aliases table legitimately uses NOT NULL for its own key
    // columns, so this is checked on ALTER statements only).
    for (String stmt : v016.split(";")) {
      if (stmt.contains("ALTER TABLE")) {
        assertFalse(stmt.contains("NOT NULL"),
            "V016 must not add any NOT NULL column; statement was: " + stmt.trim());
      }
    }
  }

  @Test void v016CreatesAliasTableWithGloballyUniqueAlias() throws Exception {
    String v016 = migrationSql();
    assertTrue(v016.contains("CREATE TABLE IF NOT EXISTS department_aliases"),
        "V016 must create department_aliases (D-198)");
    assertTrue(v016.contains("UNIQUE KEY uk_dept_alias (alias)"),
        "V016 must make alias globally unique so one alias maps to exactly one canonical row (D-198)");
    assertTrue(v016.contains("FOREIGN KEY (catalog_id) REFERENCES department_catalog(id) ON DELETE RESTRICT"),
        "V016 must link aliases to the catalog with RESTRICT (D-198)");
    assertTrue(v016.contains("CREATE INDEX idx_deptalias_catalog ON department_aliases (catalog_id)"),
        "V016 must index department_aliases.catalog_id as its own statement (D-188)");
  }

  @Test void v016SeedsCanonicalDisciplines() throws Exception {
    String v016 = migrationSql();
    for (String name : new String[]{"Software Engineering", "Civil Engineering",
        "Mechanical Engineering", "Industrial and Production Engineering", "Architecture",
        "Pharmacy", "Biotechnology", "Genetic Engineering", "Microbiology",
        "Chemistry", "Physics", "Mathematics", "Statistics", "Economics",
        "Business Administration", "Accounting", "Finance", "Marketing",
        "Management", "English", "Law", "Sociology", "Political Science",
        "Psychology", "Journalism & Media Studies", "Public Administration",
        "Environmental Science", "Agriculture", "Fisheries", "Veterinary Science"}) {
      assertTrue(v016.contains("'" + name + "'"),
          "V016 must seed canonical discipline " + name + " (D-198)");
    }
  }

  @Test void v016DoesNotSeedUnverifiedMedicalDisciplines() throws Exception {
    String v016 = migrationSql();
    // MBBS/BDS/Nursing are seeded only where a university source confirms
    // them (D-198); no such source was read in this session, so they must
    // not appear anywhere in the migration — not even as a comment seed.
    assertFalse(v016.contains("MBBS"), "V016 must not seed MBBS (not verified — research doc §3/§5)");
    assertFalse(v016.contains("BDS"), "V016 must not seed BDS (not verified — research doc §3/§5)");
    assertFalse(v016.contains("Nursing"), "V016 must not seed Nursing (not verified — research doc §3/§5)");
  }

  @Test void v016SeedIsInsertIgnoreOnly() throws Exception {
    String v016 = migrationSql();
    assertFalse(v016.contains("REPLACE INTO"), "V016 seed must not use REPLACE INTO");
    assertFalse(v016.contains("ON DUPLICATE KEY"), "V016 seed must not use ON DUPLICATE KEY UPDATE");
    for (String stmt : v016.split(";")) {
      String t = stmt.trim().toUpperCase();
      if (t.startsWith("INSERT INTO DEPARTMENT_CATALOG") || t.startsWith("INSERT INTO DEPARTMENT_ALIASES")
          || t.startsWith("INSERT INTO DEPARTMENTS")) {
        assertTrue(stmt.contains("INSERT IGNORE"),
            "every V016 seed row must be INSERT IGNORE; statement was: " + stmt.trim());
      }
    }
  }

  @Test void v016NeverCrossJoinsDepartments() throws Exception {
    String v016 = migrationSql();
    // The V001 cross-join seed pattern is explicitly forbidden for links
    // (D-198): it would invent unverified university->department mappings.
    assertFalse(v016.toUpperCase().contains("CROSS JOIN"),
        "V016 must never CROSS JOIN when seeding departments link rows (D-198)");
    // Every link row names its university and catalog explicitly.
    assertTrue(v016.contains("WHERE u.name = 'Metropolitan University'"),
        "V016 link rows must name Metropolitan University explicitly");
    assertTrue(v016.contains("WHERE u.name = 'University of Dhaka'"),
        "V016 link rows must name the University of Dhaka explicitly");
    assertTrue(v016.contains("ON c.name = 'Computer Science and Engineering'"),
        "V016 link rows must name the CSE catalog row explicitly");
    assertTrue(v016.contains("ON c.name = 'Electrical and Electronic Engineering'"),
        "V016 link rows must name the EEE catalog row explicitly");
  }

  @Test void v016AliasSeedsResolveCatalogIdByName() throws Exception {
    String v016 = migrationSql();
    // Alias rows must resolve catalog_id by name at insert time, never
    // hardcode a numeric catalog id (ids differ between databases).
    assertTrue(v016.contains("FROM department_catalog WHERE name = 'Computer Science and Engineering'"),
        "V016 alias seeds must resolve the catalog row by name");
    assertFalse(v016.matches("(?s).*INSERT IGNORE INTO department_aliases \\(catalog_id, alias\\) VALUES \\(\\d+.*"),
        "V016 alias seeds must not hardcode numeric catalog ids");
  }

  @Test void v016DoesNotDropOrDeleteAnything() throws Exception {
    String v016 = migrationSql();
    assertFalse(v016.contains("DROP TABLE"), "V016 must not drop tables");
    assertFalse(v016.contains("DROP COLUMN"), "V016 must not drop columns");
    assertFalse(v016.contains("DELETE FROM"), "V016 must not delete rows");
    assertFalse(v016.contains("TRUNCATE"), "V016 must not truncate");
  }

  private static String migrationSql() throws Exception {
    try (InputStream in = MigrationV016Test.class.getResourceAsStream("/db/migration/V016__department_catalogue.sql")) {
      assertNotNull(in, "V016 must be on the classpath");
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      // Analyse SQL, not comments: the header explains the TiDB rules in
      // words ("ADD COLUMN", "ADD INDEX", "CROSS JOIN", "MBBS") that must
      // not be counted as DDL.
      return sql.lines().filter(l -> !l.trim().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
    }
  }
}
