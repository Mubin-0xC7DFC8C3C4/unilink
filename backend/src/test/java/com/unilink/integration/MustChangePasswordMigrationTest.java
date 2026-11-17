package com.unilink.integration;

import com.unilink.identity.domain.AdminAccount;
import com.unilink.identity.domain.AdminRecoveryCode;
import com.unilink.identity.domain.UserCredential;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Static regression test for V010 (D-097): the migration adds the
 *  must-change / temp-expiry columns and widens the recovery-code hash, and
 *  the entities map them. A live Flyway run is still impossible in this
 *  sandbox (Java→MySQL is intercepted), so — like MigrationColumnsTest for
 *  V008 — this asserts the facts that together close the gap: the entities
 *  map the fields, V002/V003 never created them, and V010 adds them. */
class MustChangePasswordMigrationTest {
  @Test void entitiesMapNewColumns() throws Exception {
    assertEquals("must_change_password", columnName(UserCredential.class, "mustChangePassword"));
    assertEquals("temp_password_expires_at", columnName(UserCredential.class, "tempPasswordExpiresAt"));
    assertEquals("must_change_password", columnName(AdminAccount.class, "mustChangePassword"));
    assertEquals("temp_password_expires_at", columnName(AdminAccount.class, "tempPasswordExpiresAt"));
    assertEquals("expires_at", columnName(AdminRecoveryCode.class, "expiresAt"));
  }

  @Test void v002AndV003NeverCreatedTheColumns() throws Exception {
    assertFalse(createTableBlock("V002__identity.sql", "user_credentials").contains("must_change_password"));
    assertFalse(createTableBlock("V002__identity.sql", "user_credentials").contains("temp_password_expires_at"));
    assertFalse(createTableBlock("V003__admin.sql", "admin_accounts").contains("must_change_password"));
    assertFalse(createTableBlock("V003__admin.sql", "admin_accounts").contains("temp_password_expires_at"));
    // V003 sized the hash for SHA-256 hex only — that is why V010 widens it.
    assertTrue(createTableBlock("V003__admin.sql", "admin_recovery_codes").contains("code_hash CHAR(64)"));
  }

  @Test void v010AddsAllColumnsAndWidensCodeHash() throws Exception {
    String v010 = migrationSql("V010__must_change_password_and_temp_expiry.sql");
    assertTrue(v010.contains("ALTER TABLE user_credentials ADD COLUMN must_change_password"));
    assertTrue(v010.contains("ADD COLUMN temp_password_expires_at"));
    assertTrue(v010.contains("ALTER TABLE admin_accounts ADD COLUMN must_change_password"));
    assertTrue(v010.contains("ALTER TABLE admin_recovery_codes MODIFY COLUMN code_hash VARCHAR(255)"));
    assertTrue(v010.contains("ADD COLUMN expires_at"));
  }

  private static String migrationSql(String file) throws Exception {
    try (InputStream in = MustChangePasswordMigrationTest.class.getResourceAsStream("/db/migration/" + file)) {
      assertNotNull(in, file + " must be on the classpath");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String columnName(Class<?> entity, String field) throws Exception {
    Column c = entity.getDeclaredField(field).getAnnotation(Column.class);
    assertNotNull(c, entity.getSimpleName() + "." + field + " must carry @Column");
    return c.name();
  }

  private static String createTableBlock(String file, String table) throws Exception {
    String sql = migrationSql(file);
    int start = sql.indexOf("CREATE TABLE IF NOT EXISTS " + table);
    assertTrue(start >= 0, file + " must create " + table);
    int end = sql.indexOf(";", start);
    return sql.substring(start, end);
  }
}
