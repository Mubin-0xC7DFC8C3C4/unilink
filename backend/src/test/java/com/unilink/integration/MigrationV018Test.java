package com.unilink.integration;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Static regression tests for V018 (D-200): the migration is MySQL/TiDB
 *  DDL that H2 cannot run, so — following the MigrationV014Test /
 *  MigrationV015Test / MigrationV016Test / MigrationV017Test pattern —
 *  this asserts the facts that together close the gap. The chain itself
 *  must still be verified by a genuine Flyway run against real MySQL on a
 *  scratch database (see the D-188 procedure): H2-only verification is not
 *  enough. */
class MigrationV018Test {

  @Test void v018CreatesConnectionRequestsTableWithUniqueKey() throws Exception {
    String v018 = migrationSql();
    assertTrue(v018.contains("CREATE TABLE IF NOT EXISTS connection_requests"),
        "V018 must create connection_requests");
    assertTrue(v018.contains("UNIQUE KEY uk_conn_req (requester_id, addressee_id, status)"),
        "V018 must block duplicate PENDING rows with UNIQUE (requester_id, addressee_id, status) (D-200)");
    assertTrue(v018.contains("status VARCHAR(32) NOT NULL DEFAULT 'PENDING'"),
        "V018 status must default to PENDING");
    assertTrue(v018.contains("message VARCHAR(512) NULL"),
        "V018 must carry an optional message (D-200)");
    assertTrue(v018.contains("decided_at DATETIME(3) NULL"),
        "V018 must carry decided_at (D-200)");
    assertTrue(v018.contains("FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE"),
        "V018 requester FK must cascade on user delete");
    assertTrue(v018.contains("FOREIGN KEY (addressee_id) REFERENCES users(id) ON DELETE CASCADE"),
        "V018 addressee FK must cascade on user delete");
  }

  @Test void v018IndexesAreSeparateStatements() throws Exception {
    String v018 = migrationSql();
    assertTrue(v018.contains("CREATE INDEX idx_cr_addressee_pending ON connection_requests (addressee_id, status)"),
        "V018 must index the addressee inbox lookup");
    assertTrue(v018.contains("CREATE INDEX idx_cr_requester_pending ON connection_requests (requester_id, status)"),
        "V018 must index the requester outgoing lookup");
    // TiDB rule (D-188): one schema change per statement — each CREATE
    // INDEX must be its own statement, never folded into the CREATE TABLE.
    for (String stmt : v018.split(";")) {
      if (stmt.contains("CREATE TABLE IF NOT EXISTS connection_requests")) {
        assertFalse(stmt.contains("CREATE INDEX"),
            "V018 must not put CREATE INDEX inside the CREATE TABLE statement; statement was: " + stmt.trim());
      }
    }
  }

  @Test void v018DoesNotDropOrDeleteAnything() throws Exception {
    String v018 = migrationSql();
    assertFalse(v018.contains("DROP TABLE"), "V018 must not drop tables");
    assertFalse(v018.contains("DROP COLUMN"), "V018 must not drop columns");
    assertFalse(v018.contains("DELETE FROM"), "V018 must not delete rows");
    assertFalse(v018.contains("TRUNCATE"), "V018 must not truncate");
    assertFalse(v018.contains("REPLACE INTO"), "V018 must not use REPLACE INTO");
    assertFalse(v018.contains("ON DUPLICATE KEY"), "V018 must not use ON DUPLICATE KEY UPDATE");
  }

  private static String migrationSql() throws Exception {
    try (InputStream in = MigrationV018Test.class.getResourceAsStream("/db/migration/V018__connection_requests.sql")) {
      assertNotNull(in, "V018 must be on the classpath");
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      // Analyse SQL, not comments: the header explains the TiDB rules in
      // words ("CREATE INDEX") that must not be counted as DDL.
      return sql.lines().filter(l -> !l.trim().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
    }
  }
}
