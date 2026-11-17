package com.unilink.integration;
import com.unilink.identity.domain.NotificationPreference;
import com.unilink.identity.domain.VerificationRequest;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
/** Regression test for M1/M2: the entities VerificationRequest and
 *  NotificationPreference map a createdAt field to a created_at column that
 *  migrations V002/V006 never created.
 *
 *  Why a static check and not a live Flyway run: the production migrations
 *  use MySQL-only DDL (a `year` column, `FULLTEXT KEY`) that H2 cannot
 *  parse, and Java processes cannot reach MySQL in this sandbox, so no
 *  in-test database can run the real chain. This test therefore asserts the
 *  three facts that together close the gap: (1) both entities map createdAt
 *  to created_at, (2) neither V002 nor V006 creates that column, and
 *  (3) V008 adds it to both tables. Before the fix there was no V008, so
 *  this test fails on the pre-fix tree. The chain itself must still be
 *  verified by a genuine startup against real MySQL. */
class MigrationColumnsTest {
  @Test void entitiesMapCreatedAtToCreatedAt() throws Exception {
    assertEquals("created_at", columnName(VerificationRequest.class, "createdAt"));
    assertEquals("created_at", columnName(NotificationPreference.class, "createdAt"));
  }

  @Test void v002AndV006NeverCreatedTheColumn() throws Exception {
    assertFalse(createTableBlock("V002__identity.sql", "verification_requests").contains("created_at"),
        "V002 must not define created_at on verification_requests (that is why V008 exists)");
    assertFalse(createTableBlock("V006__moderation_notifications_audit.sql", "notification_preferences").contains("created_at"),
        "V006 must not define created_at on notification_preferences (that is why V008 exists)");
  }

  @Test void v008AddsMissingCreatedAtColumns() throws Exception {
    String v008 = migrationSql("V008__missing_created_at_columns.sql");
    assertTrue(v008.contains("ALTER TABLE verification_requests ADD COLUMN created_at"),
        "V008 must add created_at to verification_requests");
    assertTrue(v008.contains("ALTER TABLE notification_preferences ADD COLUMN created_at"),
        "V008 must add created_at to notification_preferences");
  }

  private static String migrationSql(String file) throws Exception {
    try (InputStream in = MigrationColumnsTest.class.getResourceAsStream("/db/migration/" + file)) {
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
