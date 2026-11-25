package com.unilink.integration;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Static regression tests for V019 (D-203): the migration is MySQL/TiDB
 *  DDL that H2 cannot run, so — following the MigrationV018Test pattern —
 *  this asserts the facts that together close the gap. The chain itself
 *  must still be verified by a genuine Flyway run against real MySQL on a
 *  scratch database (see the D-188 procedure): H2-only verification is not
 *  enough. */
class MigrationV019Test {

  @Test void v019AddsNullablePinColumnsToProjects() throws Exception {
    String v019 = migrationSql();
    assertTrue(v019.contains("ALTER TABLE projects ADD COLUMN github_owner VARCHAR(255) NULL"),
        "V019 must add nullable github_owner to projects");
    assertTrue(v019.contains("ALTER TABLE projects ADD COLUMN github_repo VARCHAR(255) NULL"),
        "V019 must add nullable github_repo to projects");
    // TiDB rule (D-188): one schema change per statement — each ALTER must
    // be its own statement, never two columns in one ALTER.
    for (String stmt : v019.split(";")) {
      if (stmt.contains("ADD COLUMN github_owner")) assertFalse(stmt.contains("github_repo"),
        "V019 must not add both pin columns in one ALTER; statement was: " + stmt.trim());
      if (stmt.contains("ADD COLUMN github_repo")) assertFalse(stmt.contains("github_owner"),
        "V019 must not add both pin columns in one ALTER; statement was: " + stmt.trim());
    }
  }

  @Test void v019CreatesOAuthStateTableWithUniqueNonce() throws Exception {
    String v019 = migrationSql();
    assertTrue(v019.contains("CREATE TABLE IF NOT EXISTS github_oauth_states"),
        "V019 must create github_oauth_states for single-use OAuth state (D-203)");
    assertTrue(v019.contains("UNIQUE KEY uk_gos_nonce (nonce)"),
        "V019 must enforce one row per nonce so a state cannot be consumed twice");
    assertTrue(v019.contains("expires_at DATETIME(3) NOT NULL"),
        "V019 state rows must carry an expiry");
    assertTrue(v019.contains("used_at DATETIME(3) NULL"),
        "V019 state rows must carry a consumed marker");
    assertTrue(v019.contains("FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"),
        "V019 state rows must cascade on user delete");
  }

  @Test void v019DoesNotDropOrDeleteAnything() throws Exception {
    String v019 = migrationSql();
    assertFalse(v019.contains("DROP TABLE"), "V019 must not drop tables");
    assertFalse(v019.contains("DROP COLUMN"), "V019 must not drop columns");
    assertFalse(v019.contains("DELETE FROM"), "V019 must not delete rows");
    assertFalse(v019.contains("TRUNCATE"), "V019 must not truncate");
    assertFalse(v019.contains("REPLACE INTO"), "V019 must not use REPLACE INTO");
    assertFalse(v019.contains("ON DUPLICATE KEY"), "V019 must not use ON DUPLICATE KEY UPDATE");
  }

  private static String migrationSql() throws Exception {
    try (InputStream in = MigrationV019Test.class.getResourceAsStream("/db/migration/V019__github_pin_projects.sql")) {
      assertNotNull(in, "V019 must be on the classpath");
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      // Analyse SQL, not comments: the header explains the TiDB rules in
      // words ("ALTER TABLE", "CREATE TABLE") that must not be counted as DDL.
      return sql.lines().filter(l -> !l.trim().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
    }
  }
}
