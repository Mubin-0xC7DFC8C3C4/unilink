package com.unilink.identity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** GET /api/health is public (I-15 / inventory G-9): a liveness probe that
 *  returns only {status, service} and no user data. It used to 401 for
 *  unauthenticated callers because it sits under /api/** and was not in the
 *  student chain's permitAll list. The control test proves auth is still
 *  enforced on a real protected endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  // H2 test DBs have no SPRING_SESSION tables (Flyway is disabled here;
  // production gets them from V007). Without this, any request that creates
  // a session fails: the request cache saves the request on an unauthenticated
  // 401, SessionRepositoryFilter then INSERTs into the missing table, and the
  // same missing table is what the every-minute cleanup cron trips over
  // (see the spring-session ERROR verdict in the Phase 2 report).
  "spring.session.jdbc.initialize-schema=always"
})
class HealthEndpointTest {
  @Autowired MockMvc mvc;
  @Test void unauthenticatedHealthIsPublic() throws Exception {
    mvc.perform(get("/api/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP"))
      .andExpect(jsonPath("$.service").value("unilink-backend"));
  }
  @Test void unauthenticatedProtectedEndpointIsStill401() throws Exception {
    // Control: auth is still enforced on a genuinely protected endpoint.
    // (Deliberately NOT /api/auth/me: it is under the permitAll /api/auth/**
    //  matcher, but AuthController.me now guards its null Authentication
    //  and answers 401 instead of 500 — the NPE was fixed in Phase 2
    //  (D-090), so /api/auth/me would be a valid control too; /api/profiles/me
    //  is kept here as the non-auth-namespaced control.)
    mvc.perform(get("/api/profiles/me"))
      .andExpect(status().isUnauthorized());
  }
}
