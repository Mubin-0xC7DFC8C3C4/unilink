package com.unilink.integration;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Static regression tests for V017 (D-199): the migration is MySQL/TiDB
 *  DDL that H2 cannot run, so — following the MigrationV014Test /
 *  MigrationV015Test / MigrationV016Test pattern — this asserts the facts
 *  that together close the gap. The chain itself must still be verified by
 *  a genuine Flyway run against real MySQL on a scratch database (see the
 *  D-188 procedure): H2-only verification is not enough. */
class MigrationV017Test {

  @Test void v017AddsAtMostOneColumnPerAlter() throws Exception {
    String v017 = migrationSql();
    for (String stmt : v017.split(";")) {
      if (stmt.contains("ALTER TABLE")) {
        int adds = stmt.split("ADD COLUMN", -1).length - 1;
        assertTrue(adds <= 1,
            "V017 must add at most one column per ALTER (TiDB multi-schema restriction); statement was: " + stmt.trim());
        assertFalse(stmt.contains("ADD INDEX"),
            "V017 must not ADD INDEX in the same ALTER that adds a column (TiDB Error 1072); statement was: " + stmt.trim());
      }
    }
  }

  @Test void v017AddsPendingPhoneColumnsNullable() throws Exception {
    String v017 = migrationSql();
    assertTrue(v017.contains("ADD COLUMN pending_phone VARCHAR(32) NULL"),
        "V017 must add users.pending_phone VARCHAR(32) NULL (D-199)");
    assertTrue(v017.contains("ADD COLUMN pending_phone_requested_at DATETIME(3) NULL"),
        "V017 must add users.pending_phone_requested_at DATETIME(3) NULL (D-199)");
    for (String stmt : v017.split(";")) {
      if (stmt.contains("ALTER TABLE")) {
        assertTrue(stmt.contains("NULL"),
            "V017 ALTERs must add NULLABLE columns (legacy rows stay untouched); statement was: " + stmt.trim());
        assertFalse(stmt.contains("NOT NULL"),
            "V017 must not add any NOT NULL column; statement was: " + stmt.trim());
      }
    }
  }

  @Test void v017TouchesUsersOnly() throws Exception {
    String v017 = migrationSql();
    for (String stmt : v017.split(";")) {
      if (stmt.contains("ALTER TABLE")) {
        assertTrue(stmt.contains("ALTER TABLE users"),
            "V017 must only alter users; statement was: " + stmt.trim());
      }
    }
  }

  @Test void v017DoesNotDropOrDeleteAnything() throws Exception {
    String v017 = migrationSql();
    assertFalse(v017.contains("DROP TABLE"), "V017 must not drop tables");
    assertFalse(v017.contains("DROP COLUMN"), "V017 must not drop columns");
    assertFalse(v017.contains("DELETE FROM"), "V017 must not delete rows");
    assertFalse(v017.contains("TRUNCATE"), "V017 must not truncate");
    assertFalse(v017.contains("REPLACE INTO"), "V017 must not use REPLACE INTO");
    assertFalse(v017.contains("ON DUPLICATE KEY"), "V017 must not use ON DUPLICATE KEY UPDATE");
  }

  private static String migrationSql() throws Exception {
    try (InputStream in = MigrationV017Test.class.getResourceAsStream("/db/migration/V017__phone_change_pending.sql")) {
      assertNotNull(in, "V017 must be on the classpath");
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      // Analyse SQL, not comments: the header explains the TiDB rules in
      // words ("ADD COLUMN", "ADD INDEX") that must not be counted as DDL.
      return sql.lines().filter(l -> !l.trim().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
    }
  }
}
