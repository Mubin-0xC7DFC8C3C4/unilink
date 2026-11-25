package com.unilink.integration;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Static regression tests for V015 (D-190–D-194): the migration is
 *  MySQL/TiDB DDL that H2 cannot run, so — following the MigrationV014Test
 *  pattern — this asserts the facts that together close the gap. The chain
 *  itself must still be verified by a genuine Flyway run against real
 *  MySQL on a representative production-schema copy (see the D-188
 *  procedure): H2-only verification is not enough. */
class MigrationV015Test {

  @Test void v015AddsAtMostOneColumnPerAlter() throws Exception {
    String v015 = migrationSql();
    // TiDB before 6.2 rejects adding multiple columns in one ALTER, and
    // the production cluster's TiDB version is not confirmed.
    for (String stmt : v015.split(";")) {
      if (stmt.contains("ALTER TABLE")) {
        int adds = stmt.split("ADD COLUMN", -1).length - 1;
        assertTrue(adds <= 1,
            "V015 must add at most one column per ALTER (TiDB multi-schema restriction); statement was: " + stmt.trim());
        assertFalse(stmt.contains("ADD INDEX"),
            "V015 must not ADD INDEX in the same ALTER that adds a column (TiDB Error 1072); statement was: " + stmt.trim());
      }
    }
  }

  @Test void v015AddsAllNewColumnsNullable() throws Exception {
    String v015 = migrationSql();
    assertTrue(v015.contains("ADD COLUMN category VARCHAR(16) NULL"),
        "V015 must add universities.category (D-192)");
    assertTrue(v015.contains("ADD COLUMN department_label VARCHAR(255) NULL"),
        "V015 must add users.department_label (D-193)");
    assertTrue(v015.contains("ADD COLUMN batch_label VARCHAR(64) NULL"),
        "V015 must add users.batch_label (D-194)");
    assertTrue(v015.contains("ADD COLUMN id_card_back_key VARCHAR(512) NULL"),
        "V015 must add verification_requests.id_card_back_key (D-190)");
    assertTrue(v015.contains("ADD COLUMN ocr_text_back MEDIUMTEXT NULL"),
        "V015 must add verification_requests.ocr_text_back (D-191)");
    // Every new column is NULLABLE: legacy rows keep working, nothing is
    // dropped or overwritten.
    assertFalse(v015.contains("NOT NULL"), "V015 must not add any NOT NULL column");
  }

  @Test void v015MakesDepartmentIdNullable() throws Exception {
    String v015 = migrationSql();
    assertTrue(v015.contains("MODIFY COLUMN department_id BIGINT UNSIGNED NULL"),
        "V015 must make users.department_id nullable (D-193) with its original UNSIGNED type");
  }

  @Test void v015ClassifiesTheSeededUniversities() throws Exception {
    String v015 = migrationSql();
    assertTrue(v015.contains("SET category = 'PRIVATE' WHERE name = 'Metropolitan University'"),
        "V015 must classify Metropolitan University as PRIVATE (UGC)");
    assertTrue(v015.contains("SET category = 'PUBLIC' WHERE name = 'University of Dhaka'"),
        "V015 must classify the University of Dhaka as PUBLIC (UGC)");
  }

  @Test void v015SeedIsInsertIgnoreOnly() throws Exception {
    String v015 = migrationSql();
    // The UGC seed must be re-runnable and must never duplicate or
    // overwrite: INSERT IGNORE, no REPLACE, no ON DUPLICATE KEY UPDATE.
    assertFalse(v015.contains("REPLACE INTO"), "V015 seed must not use REPLACE INTO");
    assertFalse(v015.contains("ON DUPLICATE KEY"), "V015 seed must not use ON DUPLICATE KEY UPDATE");
    for (String stmt : v015.split(";")) {
      if (stmt.trim().toUpperCase().startsWith("INSERT INTO UNIVERSITIES")) {
        assertTrue(stmt.contains("INSERT IGNORE"),
            "every university seed row must be INSERT IGNORE; statement was: " + stmt.trim());
      }
    }
  }

  @Test void v015DoesNotDropOrDeleteAnything() throws Exception {
    String v015 = migrationSql();
    assertFalse(v015.contains("DROP TABLE"), "V015 must not drop tables");
    assertFalse(v015.contains("DROP COLUMN"), "V015 must not drop columns");
    assertFalse(v015.contains("DELETE FROM"), "V015 must not delete rows");
    assertFalse(v015.contains("TRUNCATE"), "V015 must not truncate");
  }

  private static String migrationSql() throws Exception {
    try (InputStream in = MigrationV015Test.class.getResourceAsStream("/db/migration/V015__two_sided_id_ugc_catalogue_free_text_batch.sql")) {
      assertNotNull(in, "V015 must be on the classpath");
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      // Analyse SQL, not comments: the header explains the TiDB rules in
      // words ("ADD COLUMN", "ADD INDEX") that must not be counted as DDL.
      return sql.lines().filter(l -> !l.trim().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
    }
  }
}
