package com.unilink.integration;

import com.unilink.admin.AdminService;
import com.unilink.common.Exceptions;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.LoginAttemptRepository;
import com.unilink.identity.repo.UserCredentialRepository;
import com.unilink.identity.repo.UserRepository;
import com.unilink.identity.service.AuthService;
import com.unilink.security.LoginThrottle;
import com.unilink.security.Principals;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Worker 3 audit (§4D): the two security chains wire their own
 *  UserDetailsService / AuthenticationManager with no ambiguity, the
 *  student and admin domains never cross-authenticate, unauthenticated
 *  admin access is 401, non-MFA / student access to admin endpoints is
 *  403, and CSRF is enforced on non-exempt mutations. MockMvc on H2;
 *  sessions are replayed via the cookies the login responses set. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-secchains;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-secchains-key",
  // H2 test DBs have no SPRING_SESSION tables (Flyway is disabled here);
  // without this, any request that creates a session fails.
  "spring.session.jdbc.initialize-schema=always"
})
@Transactional
class SecurityChainsTest {
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext ctx;
  @Autowired TestDataFactory factory;
  @Autowired AuthService authService;
  @Autowired AdminService adminService;
  @Autowired UserRepository users;
  @Autowired UserCredentialRepository creds;
  @Autowired LoginAttemptRepository loginAttempts;
  @Autowired LoginThrottle throttle;

  @BeforeEach void setup() { throttle.resetForTests(); TestProviders.reset(); }

  private String email(String prefix) { return prefix + "-" + System.nanoTime() + "@example.com"; }
  private User student(String prefix) {
    var fx = factory.seedMaster();
    return factory.verifiedStudent(email(prefix), "S" + System.nanoTime() % 100000, fx.mu(), fx.muCse(), fx.muBatch());
  }
  private Cookie[] loginAsStudent(User u) throws Exception {
    MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"Student123\"}"))
      .andExpect(status().isOk()).andReturn();
    return r.getResponse().getCookies();
  }
  private Cookie[] loginAsAdmin(String adminEmail) throws Exception {
    MvcResult r = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + adminEmail + "\",\"password\":\"Admin12345678\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.stage").value("NEEDS_ENROLMENT"))
      .andReturn();
    return r.getResponse().getCookies();
  }

  // ---- UserDetailsService / AuthenticationManager wiring ----
  @Test void bothUserDetailsServicesExistDistinctAndStudentManagerIsPrimary() {
    var studentUds = ctx.getBean("studentUserDetailsService", UserDetailsService.class);
    var adminUds = ctx.getBean("adminUserDetailsService", UserDetailsService.class);
    assertNotNull(studentUds);
    assertNotNull(adminUds);
    assertNotSame(studentUds, adminUds, "the two chains must not share a UserDetailsService");
    // @Primary (Phase 1): a by-type AuthenticationManager resolves to the student one.
    assertSame(ctx.getBean("studentAuthManager", AuthenticationManager.class),
      ctx.getBean(AuthenticationManager.class));
    assertNotNull(ctx.getBean("adminAuthManager", AuthenticationManager.class));
  }

  // ---- Each chain authenticates its own domain ----
  @Test void studentLoginSucceedsThroughStudentChain() throws Exception {
    User u = student("chain-stu");
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"Student123\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(u.getEmail()))
      .andExpect(jsonPath("$.mustChangePassword").value(false));
  }

  @Test void adminLoginSucceedsThroughAdminChain() throws Exception {
    var fx = factory.seedMaster();
    var a = factory.admin(email("chain-adm"), "UNIVERSITY_MODERATOR", fx.mu().getId());
    mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + a.getEmail() + "\",\"password\":\"Admin12345678\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.stage").value("NEEDS_ENROLMENT"))
      .andExpect(jsonPath("$.mustChangePassword").value(false));
  }

  // ---- No cross-authentication between the domains ----
  @Test void studentCredentialsDoNotAuthenticateOnAdminChain() throws Exception {
    User u = student("chain-x1");
    mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"Student123\"}"))
      // D-139: was 400; a failed sign-in is 401.
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test void adminCredentialsDoNotAuthenticateOnStudentChain() throws Exception {
    var fx = factory.seedMaster();
    var a = factory.admin(email("chain-x2"), "UNIVERSITY_MODERATOR", fx.mu().getId());
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + a.getEmail() + "\",\"password\":\"Admin12345678\"}"))
      // D-139: was 400; a failed sign-in is 401.
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  // ---- 401 / 403 boundaries ----
  @Test void unauthenticatedAdminEndpointIs401() throws Exception {
    mvc.perform(get("/api/admin/moderators")).andExpect(status().isUnauthorized());
  }

  @Test void studentSessionCannotReachAdminEndpoints() throws Exception {
    Cookie[] cookies = loginAsStudent(student("chain-x3"));
    mvc.perform(get("/api/admin/moderators").cookie(cookies)).andExpect(status().isForbidden());
  }

  @Test void adminWithoutMfaCannotReachAdminEndpoints() throws Exception {
    var fx = factory.seedMaster();
    var a = factory.admin(email("chain-x4"), "UNIVERSITY_MODERATOR", fx.mu().getId());
    Cookie[] cookies = loginAsAdmin(a.getEmail());
    mvc.perform(get("/api/admin/moderators").cookie(cookies)).andExpect(status().isForbidden());
  }

  // ---- CSRF is enforced on non-exempt mutations ----
  @Test void csrfIsEnforcedOnAccountDeletionWithoutToken() throws Exception {
    Cookie[] cookies = loginAsStudent(student("chain-x5"));
    mvc.perform(post("/api/me/delete").cookie(cookies).contentType(MediaType.APPLICATION_JSON)
        .content("{\"password\":\"Student123\"}"))
      .andExpect(status().isForbidden());
  }

  // Production 2026-11-25: with a real session and the masked CSRF token
  // from /api/csrf, deleting the account must succeed and the login must
  // then fail. (The raw XSRF-TOKEN cookie value is rejected by the XOR
  // handler — that is what the frontend sends today, see the client fix.)
  @Test void accountDeletionSucceedsWithSessionAndEndpointCsrfToken() throws Exception {
    User u = student("chain-del");
    Cookie[] cookies = loginAsStudent(u);
    MvcResult csrf = mvc.perform(get("/api/csrf").cookie(cookies))
        .andExpect(status().isOk()).andReturn();
    String token = new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(csrf.getResponse().getContentAsString()).get("token").asText();
    assertFalse(token.isBlank());
    // GET /csrf may refresh the XSRF-TOKEN cookie; the delete must carry
    // the cookies the csrf response set, not the stale login ones.
    java.util.Map<String, Cookie> byName = new java.util.LinkedHashMap<>();
    for (Cookie c : cookies) byName.put(c.getName(), c);
    for (Cookie c : csrf.getResponse().getCookies()) byName.put(c.getName(), c);
    cookies = byName.values().toArray(new Cookie[0]);
    mvc.perform(post("/api/me/delete").cookie(cookies).contentType(MediaType.APPLICATION_JSON)
        .header("X-XSRF-TOKEN", token)
        .content("{\"password\":\"Student123\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
    assertTrue(users.findById(u.getId()).isEmpty(), "account must be gone after delete");
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"Student123\"}"))
      .andExpect(status().isUnauthorized());
  }

  // The raw XSRF-TOKEN cookie value is NOT a valid header token (the
  // XOR handler masks it): sending it raw must fail with 403, exactly
  // as the frontend did before the client fix (production 2026-11-25).
  @Test void rawCookieCsrfTokenIsRejectedOnAccountDeletion() throws Exception {
    User u = student("chain-del-raw");
    Cookie[] cookies = loginAsStudent(u);
    MvcResult csrf = mvc.perform(get("/api/csrf").cookie(cookies))
        .andExpect(status().isOk()).andReturn();
    String raw = null;
    for (Cookie c : csrf.getResponse().getCookies())
      if ("XSRF-TOKEN".equals(c.getName())) raw = c.getValue();
    assertNotNull(raw, "csrf priming must set the XSRF-TOKEN cookie");
    mvc.perform(post("/api/me/delete").cookie(cookies).contentType(MediaType.APPLICATION_JSON)
        .header("X-XSRF-TOKEN", raw)
        .content("{\"password\":\"Student123\"}"))
      .andExpect(status().isForbidden());
    assertTrue(users.findById(u.getId()).isPresent(), "a rejected delete must leave the account alone");
  }

  // ---- D-121: suspended / expired-temp logins leave a login_attempts row ----
  @Test void suspendedLoginAttemptIsRecorded() {
    User u = student("chain-susp");
    u.setState("SUSPENDED");
    users.save(u);
    assertThrows(Exceptions.Forbidden.class,
      () -> authService.login(u.getEmail(), "Student123", "127.0.0.1"));
    assertTrue(loginAttempts.findAll().stream()
      .anyMatch(r -> u.getEmail().equals(r.getEmail()) && Boolean.FALSE.equals(r.getSuccess())),
      "suspended login attempt must be recorded in login_attempts");
  }

  @Test void expiredTempPasswordLoginAttemptIsRecorded() {
    User u = student("chain-exp");
    var platform = new Principals.AdminPrincipal(999L, "platform@example.com", "x", "PLATFORM_ADMIN", null, true, true);
    var reset = adminService.changeUserState(platform, u.getId(), "FORCE_RESET", "test reset", "127.0.0.1");
    var c = creds.findById(u.getId()).orElseThrow();
    c.setTempPasswordExpiresAt(LocalDateTime.now().minusMinutes(1));
    creds.save(c);
    // Log in with the (correct) temporary password: authentication succeeds,
    // then the expired-temp check rejects it with TEMP_PASSWORD_EXPIRED.
    var e = assertThrows(Exceptions.BadRequest.class,
      () -> authService.login(u.getEmail(), reset.temporaryPassword(), "127.0.0.1"));
    assertEquals("TEMP_PASSWORD_EXPIRED", e.code());
    assertTrue(loginAttempts.findAll().stream()
      .anyMatch(r -> u.getEmail().equals(r.getEmail()) && Boolean.FALSE.equals(r.getSuccess())),
      "expired-temp-password login attempt must be recorded in login_attempts");
  }
}
