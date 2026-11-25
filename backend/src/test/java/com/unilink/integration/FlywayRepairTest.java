package com.unilink.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

/** Empirical proof for the D-188 repair-on-startup bean: Flyway repair()
 *  removes FAILED history rows only and never deletes a successful
 *  migration or unrelated record. This simulates the production state
 *  exactly — a successful V1 (standing in for V001–V013), a FAILED V14
 *  row (the first auto-deploy's failed V014), plus a FAILED V9 row for
 *  an unrelated version — then repairs and migrates the fixed V14. It
 *  runs against H2, which like MySQL/TiDB does not support DDL
 *  transactions, so failed rows are recorded the same way. */
class FlywayRepairTest {

  private static final String URL =
      "jdbc:h2:mem:flywayrepair;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

  @Test void repairRemovesOnlyFailedRowsAndKeepsSuccessfulHistory() throws Exception {
    Path dir = Files.createTempDirectory("flyway-repair-test");
    Files.writeString(dir.resolve("V1__probe.sql"),
        "CREATE TABLE repair_probe (id INT PRIMARY KEY, v VARCHAR(20));\n"
        + "INSERT INTO repair_probe VALUES (1, 'kept');\n");

    Flyway flyway = Flyway.configure().dataSource(URL, "sa", "")
        .locations("filesystem:" + dir).load();
    flyway.migrate();

    // The fixed V14 is written only now, so the first migrate applies V1
    // alone (as in production, where V001–V013 applied before V014 was
    // ever attempted). At repair time the fixed V14 is resolved, exactly
    // as in the deployed image.
    Files.writeString(dir.resolve("V14__fixed.sql"),
        "CREATE TABLE repair_v14 (id INT PRIMARY KEY);\n"
        + "INSERT INTO repair_v14 VALUES (1);\n");

    // Simulate production: a FAILED V14 row plus a FAILED row for an
    // unrelated version, alongside the successful V1 row.
    try (Connection c = DriverManager.getConnection(URL, "sa", "");
         Statement s = c.createStatement()) {
      s.execute("INSERT INTO flyway_schema_history "
          + "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) "
          + "VALUES (2, '14', 'fixed', 'SQL', 'V14__fixed.sql', 12345, 'test', CURRENT_TIMESTAMP, 0, FALSE)");
      s.execute("INSERT INTO flyway_schema_history "
          + "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) "
          + "VALUES (3, '9', 'unrelated', 'SQL', 'V9__unrelated.sql', 67890, 'test', CURRENT_TIMESTAMP, 0, FALSE)");
    }

    long v1ChecksumBefore;
    try (Connection c = DriverManager.getConnection(URL, "sa", "");
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT checksum, success FROM flyway_schema_history WHERE version = '1'")) {
      assertTrue(rs.next(), "V1 must be recorded");
      v1ChecksumBefore = rs.getLong(1);
      assertTrue(rs.getBoolean(2), "V1 must be successful before repair");
    }

    // The exact call the FlywayConfig bean makes at startup — on a FRESH
    // Flyway instance, as in production: the first instance's history
    // cache predates the manual inserts below the Flyway API, and
    // repair() reads failed rows from that cache, so it would see no
    // failed rows at all. A fresh instance reads the history from the
    // database, exactly as the app does when it boots against TiDB.
    Flyway fresh = Flyway.configure().dataSource(URL, "sa", "")
        .locations("filesystem:" + dir).load();
    fresh.repair();

    try (Connection c = DriverManager.getConnection(URL, "sa", "");
         Statement s = c.createStatement()) {
      // The successful V1 row survives untouched (same checksum, success).
      try (ResultSet rs = s.executeQuery(
          "SELECT checksum, success FROM flyway_schema_history WHERE version = '1'")) {
        assertTrue(rs.next(), "repair must not delete the successful V1 row");
        assertEquals(v1ChecksumBefore, rs.getLong(1), "repair must not change V1's checksum");
        assertTrue(rs.getBoolean(2), "V1 must stay successful");
      }
      // Both failed rows are gone, successful or not.
      try (ResultSet rs = s.executeQuery(
          "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE")) {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1), "repair must remove every failed row");
      }
      try (ResultSet rs = s.executeQuery(
          "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL")) {
        assertTrue(rs.next());
        // Flyway also records a "<< Flyway Schema History table created >>"
        // sentinel row (installed_rank -1, version NULL); it is not a
        // migration and is excluded from the count.
        assertEquals(1, rs.getInt(1), "only the successful V1 row may remain");
      }
      // Unrelated user data is untouched.
      try (ResultSet rs = s.executeQuery("SELECT v FROM repair_probe WHERE id = 1")) {
        assertTrue(rs.next(), "repair must not touch user data");
        assertEquals("kept", rs.getString(1));
      }
    }

    // The fixed V14 now applies cleanly on top of the repaired history.
    fresh.migrate();
    try (Connection c = DriverManager.getConnection(URL, "sa", "");
         Statement s = c.createStatement()) {
      try (ResultSet rs = s.executeQuery(
          "SELECT success FROM flyway_schema_history WHERE version = '14'")) {
        assertTrue(rs.next(), "V14 must be recorded after migrate");
        assertTrue(rs.getBoolean(1), "V14 must be successful after migrate");
      }
      try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM repair_v14")) {
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1), "the fixed V14's effects must be present");
      }
      try (ResultSet rs = s.executeQuery(
          "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL")) {
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1), "history must hold exactly V1 + V14");
      }
    }
  }
}
