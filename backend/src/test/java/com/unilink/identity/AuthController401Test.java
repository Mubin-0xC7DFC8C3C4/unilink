package com.unilink.identity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Student-auth 401 (Phase 2, same class as the admin-TOTP NPE): /api/auth/**
 *  is permitAll, so an unauthenticated request reaches AuthController with a
 *  null Authentication. The old code called authn.getPrincipal()
 *  unconditionally and threw NPE (500). Every endpoint that needs a principal
 *  must answer 401 instead. */
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
  // production gets them from V007). Without this, the request cache's
  // session save on an unauthenticated 401 fails with a missing-table error.
  "spring.session.jdbc.initialize-schema=always"
})
class AuthController401Test {
  @Autowired MockMvc mvc;
  @Test void unauthenticatedMeIs401() throws Exception {
    mvc.perform(get("/api/auth/me"))
      .andExpect(status().isUnauthorized());
  }
  @Test void unauthenticatedChangePasswordIs401() throws Exception {
    mvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
        .content("{\"oldPassword\":\"Oldpass1\",\"newPassword\":\"Newpass1\"}"))
      .andExpect(status().isUnauthorized());
  }
  @Test void unauthenticatedDeleteMeIs401() throws Exception {
    // /api/me/delete is NOT under the permitAll /api/auth/** matcher: the
    // student chain requires authenticated() and its CSRF filter rejects a
    // token-less POST with 403 before the controller is reached. With a
    // valid CSRF token the chain answers 401 (never 500) — the controller's
    // null-guard is defence in depth for this endpoint.
    mvc.perform(post("/api/me/delete").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"password\":\"whatever1\"}"))
      .andExpect(status().isUnauthorized());
  }
  @Test void unauthenticatedVerificationStatusIs401() throws Exception {
    mvc.perform(get("/api/auth/verification/status"))
      .andExpect(status().isUnauthorized());
  }
  @Test void unauthenticatedResubmitIs401() throws Exception {
    mvc.perform(multipart("/api/auth/verification/resubmit"))
      .andExpect(status().isUnauthorized());
  }
}
