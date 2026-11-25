package com.unilink.integration;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Static regression tests for V014 (D-188): the migration is MySQL/TiDB
 *  DDL that H2 cannot run, so — following the MigrationV011Test pattern —
 *  this asserts the facts that together close the gap. The chain itself
 *  must still be verified by a genuine Flyway run against real TiDB:
 *  the first auto-deploy (dep-dc3aps60tbcc73d32t70) failed on V014, and
 *  the fixed V014 must apply on the production database after repair. */
class MigrationV014Test {

  @Test void v014DoesNotCombineAddColumnWithIndexOnIt() throws Exception {
    String v014 = migrationSql("V014__email_otp_and_registration_token.sql");
    // TiDB rejects ADD COLUMN + ADD INDEX on the new column in one ALTER
    // (Error 1072, "column does not exist"); MySQL accepts it, which is
    // why the defect shipped. No single ALTER may add a column and index it.
    String[] statements = v014.split(";");
    for (String stmt : statements) {
      if (stmt.contains("ADD COLUMN verification_token_hash")
          || stmt.contains("ADD COLUMN verification_token_expires_at")) {
        assertFalse(stmt.contains("ADD INDEX"),
            "V014 must not ADD INDEX in the same ALTER that adds the token columns (TiDB Error 1072)");
      }
    }
  }

  @Test void v014AddsOneColumnPerAlter() throws Exception {
    String v014 = migrationSql("V014__email_otp_and_registration_token.sql");
    // TiDB before 6.2 rejects adding multiple columns in one ALTER
    // ("Unsupported multi schema change"), and the production cluster's
    // TiDB version is not confirmed — so every ALTER adds at most one
    // column. Count ADD COLUMN occurrences per statement.
    for (String stmt : v014.split(";")) {
      if (stmt.contains("ALTER TABLE")) {
        int adds = stmt.split("ADD COLUMN", -1).length - 1;
        assertTrue(adds <= 1,
            "V014 must add at most one column per ALTER (TiDB multi-schema restriction); statement was: " + stmt.trim());
      }
    }
  }

  @Test void v014AddsBothTokenColumns() throws Exception {
    String v014 = migrationSql("V014__email_otp_and_registration_token.sql");
    assertTrue(v014.contains("ADD COLUMN verification_token_hash CHAR(64)"),
        "V014 must add verification_token_hash to verification_requests");
    assertTrue(v014.contains("ADD COLUMN verification_token_expires_at DATETIME(3)"),
        "V014 must add verification_token_expires_at to verification_requests");
  }

  @Test void v014CreatesTheTokenIndexSeparately() throws Exception {
    String v014 = migrationSql("V014__email_otp_and_registration_token.sql");
    assertTrue(v014.contains("CREATE INDEX idx_verif_token ON verification_requests (verification_token_hash)"),
        "V014 must create idx_verif_token as a separate statement after the columns exist");
  }

  @Test void v014EmailOtpsTableIsIdempotent() throws Exception {
    String v014 = migrationSql("V014__email_otp_and_registration_token.sql");
    assertTrue(v014.contains("CREATE TABLE IF NOT EXISTS email_otps"),
        "V014 must create email_otps IF NOT EXISTS so the repair + re-run after the first failed attempt is clean");
  }

  @Test void v014DoesNotDropAnything() throws Exception {
    String v014 = migrationSql("V014__email_otp_and_registration_token.sql");
    assertFalse(v014.contains("DROP TABLE"), "V014 must not drop tables");
    assertFalse(v014.contains("DROP COLUMN"), "V014 must not drop columns");
  }

  private static String migrationSql(String file) throws Exception {
    try (InputStream in = MigrationV014Test.class.getResourceAsStream("/db/migration/" + file)) {
      assertNotNull(in, file + " must be on the classpath");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
