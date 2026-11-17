package com.unilink.integration;
import com.unilink.identity.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
/** Regression test for M7: the four shared-PK lookups (UserCredential,
 *  Profile, EventDetail, AdminTotpSecret) used derived findBy* methods that
 *  referenced properties which do not exist. Any invalid derived query in ANY
 *  repository fails this slice at startup, because Spring Data validates the
 *  queries when the repositories are created. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-repo;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class RepositoryQueryTest {
  @Autowired UserCredentialRepository creds;
  @Autowired ProfileRepository profiles;
  @Autowired EventDetailRepository events;
  @Autowired AdminTotpSecretRepository totpSecrets;

  @Test void sharedPkLookupsReturnEmptyForAbsentIds(){
    assertTrue(creds.findById(999L).isEmpty());
    assertTrue(profiles.findById(999L).isEmpty());
    assertTrue(events.findById(999L).isEmpty());
    assertTrue(totpSecrets.findById(999L).isEmpty());
    assertTrue(events.findByArchivedAtIsNullAndDeadlineBefore(LocalDateTime.now()).isEmpty());
  }
}
