package com.unilink.integration;

import com.unilink.admin.AdminBootstrap;
import com.unilink.identity.domain.AdminAccount;
import com.unilink.identity.repo.AdminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Worker 3 audit (§4E): AdminBootstrap — Issue B (D-119) plus the
 *  duplicate-bootstrap guard, password rule and missing-env handling.
 *  The bootstrap is constructed directly with explicit credentials (the
 *  real AdminAccountRepository on H2): the Spring bean reads the same
 *  values from INITIAL_ADMIN_* env vars via @Value, and the test
 *  properties below force those blank so the bean itself is a no-op at
 *  context startup and cannot interfere. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-bootstrap-key",
  "INITIAL_ADMIN_EMAIL=",
  "INITIAL_ADMIN_PASSWORD="
})
@Transactional
class AdminBootstrapTest {
  @Autowired TestDataFactory factory;
  @Autowired AdminAccountRepository admins;
  @Autowired PasswordEncoder encoder;

  @BeforeEach void setup() { TestProviders.reset(); }

  private AdminBootstrap bootstrap(String email, String password) {
    return new AdminBootstrap(admins, encoder, email, password);
  }

  // ---- D-119 / Issue B: the bootstrapped admin must rotate its password ----
  @Test void bootstrapCreatesPlatformAdminWithMustChangeFlagAndTimestamps() {
    bootstrap("bootstrap@example.com", "BootstrapPass123").run(null);
    AdminAccount a = admins.findByEmail("bootstrap@example.com").orElseThrow();
    assertEquals("PLATFORM_ADMIN", a.getRole());
    assertTrue(Boolean.TRUE.equals(a.getActive()));
    assertFalse(Boolean.TRUE.equals(a.getTotpEnrolled()));
    assertTrue(Boolean.TRUE.equals(a.getMustChangePassword()),
      "bootstrap admin must carry mustChangePassword=true (Issue B)");
    assertTrue(encoder.matches("BootstrapPass123", a.getPasswordHash()));
    // Issue A sibling: the bootstrap path goes through the real repository,
    // so the entity @PrePersist timestamp fix covers it.
    assertNotNull(a.getCreatedAt(), "bootstrap admin must have createdAt set");
  }

  @Test void bootstrapIsIdempotent() {
    var b = bootstrap("bootstrap@example.com", "BootstrapPass123");
    b.run(null);
    b.run(null);
    assertEquals(1, admins.count());
  }

  @Test void bootstrapSkipsWhenAdminAlreadyExists() {
    factory.admin("existing@example.com", "PLATFORM_ADMIN", null);
    bootstrap("bootstrap@example.com", "BootstrapPass123").run(null);
    assertEquals(1, admins.count());
    assertTrue(admins.findByEmail("bootstrap@example.com").isEmpty());
  }

  @Test void bootstrapRefusesShortPassword() {
    bootstrap("bootstrap@example.com", "short11ch!").run(null);
    assertEquals(0, admins.count());
  }

  @Test void bootstrapSkipsWhenCredentialsMissing() {
    bootstrap("", "BootstrapPass123").run(null);
    bootstrap("bootstrap@example.com", "").run(null);
    bootstrap(null, null).run(null);
    assertEquals(0, admins.count());
  }
}
