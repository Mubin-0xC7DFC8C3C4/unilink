package com.unilink.audit;

import com.unilink.identity.domain.AuditLog;
import com.unilink.identity.repo.AuditLogRepository;
import jakarta.persistence.Lob;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import com.unilink.integration.TestProviders;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-170 (2026-11-25): AuditLog.metadata is stored in a JSON column (V006).
 * It was mapped @Lob, so Hibernate bound it as a binary-charset parameter
 * and MySQL/TiDB rejected the audit INSERT with error 3144 ("Cannot create
 * a JSON value from a string with CHARACTER SET 'binary'") — every account
 * deletion in production failed with 409. The mapping test below is the
 * regression guard: the annotation test fails if anyone re-adds @Lob, and
 * the write test proves the JSON mapping persists and round-trips.
 *
 * Note the H2 tests alone could never have caught this: Hibernate-generated
 * DDL makes the column LONGTEXT on H2, where the LOB binding works. The
 * production column is JSON (Flyway V006). The annotation test exists
 * precisely because the runtime behaviour diverges between the two.
 */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-audit-json;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-audit-json-key"
})
class AuditLogJsonMappingTest {

  @Autowired AuditService audit;
  @Autowired AuditLogRepository auditLogs;

  @Test void metadataIsMappedAsJsonNotLob() throws Exception {
    Field f = AuditLog.class.getDeclaredField("metadata");
    assertNull(f.getAnnotation(Lob.class),
      "metadata must not be @Lob: Hibernate binds a LOB as binary, which MySQL/TiDB reject for a JSON column (error 3144, D-170)");
    JdbcTypeCode code = f.getAnnotation(JdbcTypeCode.class);
    assertNotNull(code, "metadata must carry @JdbcTypeCode(SqlTypes.JSON)");
    assertEquals(SqlTypes.JSON, code.value());
  }

  @Test void auditWritePersistsJsonMetadata() {
    AuditLog saved = audit.write(null, "ACCOUNT_DELETED", "USER", 424242L,
      "Self-service account deletion (NFR-PRV-02, D-052)", Map.of("state", "DELETED"), "127.0.0.1");
    assertNotNull(saved.getId());
    AuditLog read = auditLogs.findById(saved.getId()).orElseThrow();
    assertEquals("{\"state\":\"DELETED\"}", read.getMetadata());
    assertEquals("ACCOUNT_DELETED", read.getAction());
    assertEquals(424242L, read.getTargetId());
  }
}
