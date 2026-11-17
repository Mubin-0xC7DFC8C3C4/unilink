package com.unilink.integration;

import com.unilink.identity.domain.MessageAttachment;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Static regression tests for V011 (Worker D): the migration is MySQL-only
 *  DDL that H2 cannot run (like V008 before it), so — following the
 *  MigrationColumnsTest pattern — this asserts the facts that together close
 *  each gap: the entity maps the field, the old migration never created the
 *  column, and V011 adds it. The chain itself must still be verified by a
 *  genuine Flyway run against real MySQL. */
class MigrationV011Test {
  @Test void messageAttachmentMapsCreatedAt() throws Exception {
    Column c = MessageAttachment.class.getDeclaredField("createdAt").getAnnotation(Column.class);
    assertNotNull(c, "MessageAttachment.createdAt must carry @Column");
    assertEquals("created_at", c.name());
  }

  @Test void v005NeverCreatedTheColumn() throws Exception {
    String block = createTableBlock("V005__teams_messaging.sql", "message_attachments");
    assertFalse(block.contains("created_at"), "V005 must not define created_at on message_attachments (that is why V011 exists)");
  }

  @Test void v011AddsMessageAttachmentCreatedAt() throws Exception {
    String v011 = migrationSql("V011__worker_d_data_integrity.sql");
    assertTrue(v011.contains("ALTER TABLE message_attachments ADD COLUMN created_at"),
        "V011 must add created_at to message_attachments");
  }

  @Test void v011ConvertsLinksToText() throws Exception {
    String v011 = migrationSql("V011__worker_d_data_integrity.sql");
    assertTrue(v011.contains("ALTER TABLE profiles MODIFY COLUMN links TEXT"),
        "V011 must convert profiles.links from JSON to TEXT (M8: the UI treats links as free text, which MySQL JSON rejects)");
  }

  @Test void v011AddsMissingForeignKeys() throws Exception {
    String v011 = migrationSql("V011__worker_d_data_integrity.sql");
    assertTrue(v011.contains("fk_appeal_appellant"), "V011 must add the appeals.appellant_id FK (M6)");
    assertTrue(v011.contains("fk_appeal_decided_by"), "V011 must add the appeals.decided_by FK (M6)");
    assertTrue(v011.contains("fk_modev_actor"), "V011 must add the moderation_events.actor_admin_id FK (M6)");
    assertTrue(v011.contains("fk_tm_event_post"), "V011 must add the team_members.event_post_id FK (M6)");
  }

  @Test void v011DoesNotRewriteEarlierMigrations() throws Exception {
    // V011 is additive only: it must not contain CREATE TABLE for tables the
    // earlier migrations own, and must not DROP anything.
    String v011 = migrationSql("V011__worker_d_data_integrity.sql");
    assertFalse(v011.contains("CREATE TABLE"), "V011 must not create tables");
    assertFalse(v011.contains("DROP TABLE"), "V011 must not drop tables");
  }

  private static String migrationSql(String file) throws Exception {
    try (InputStream in = MigrationV011Test.class.getResourceAsStream("/db/migration/" + file)) {
      assertNotNull(in, file + " must be on the classpath");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String createTableBlock(String file, String table) throws Exception {
    String sql = migrationSql(file);
    int start = sql.indexOf("CREATE TABLE IF NOT EXISTS " + table);
    assertTrue(start >= 0, file + " must create " + table);
    int end = sql.indexOf(";", start);
    return sql.substring(start, end);
  }
}
