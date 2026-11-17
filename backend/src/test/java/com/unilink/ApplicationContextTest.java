package com.unilink;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
/** Boots the whole application context against an in-memory H2 database
 *  (MySQL mode). Regression test for the M7 repository failures: Spring Data
 *  validates every derived query when the repositories are created, so a bad
 *  findBy* method fails this test at startup; a missing bean or a broken
 *  dependency-injection wiring fails it too.
 *
 *  Flyway is disabled here on purpose: the production migrations use
 *  MySQL-only DDL that H2 cannot parse (a `year` column, `FULLTEXT KEY`),
 *  so running them against H2 proves nothing about MySQL and fails for the
 *  wrong reason. Hibernate builds the schema from the entities instead
 *  (ddl-auto=create-drop). The migration chain itself is covered by
 *  MigrationColumnsTest and must be verified against real MySQL by a genuine
 *  startup, which the sandbox currently blocks for Java processes. This test
 *  therefore does NOT prove MySQL connectivity. */
@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ApplicationContextTest {
  @Test void contextLoads() {
    // The test passes when the context boots: all beans wired, all
    // repository queries derived, all entities mapped.
  }
}
