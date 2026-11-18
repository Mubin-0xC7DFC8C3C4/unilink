package com.unilink.common;

import com.unilink.security.Principals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Worker 4 (API/error handling) audit §4F: error responses must use the
 * right status and the ApiError JSON shape — never a 500 for a client
 * error, never a container HTML error page, never a 200 for a failed or
 * junk operation. Written against the pre-fix behaviour first: every
 * test below failed on HEAD c0d6ab7 (see report for the observed status),
 * then passed after the fix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-apierr;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "spring.session.jdbc.initialize-schema=always"
})
class ApiErrorHandlingTest {
  @Autowired MockMvc mvc;

  private static Principals.UnilinkUserPrincipal student() {
    return new Principals.UnilinkUserPrincipal(7L, "s@example.com", "x", 1L, 2L, 3L, "VERIFIED", true);
  }
  private static Principals.AdminPrincipal admin() {
    return new Principals.AdminPrincipal(1L, "a@example.com", "x", "PLATFORM_ADMIN", null, true, true);
  }

  @Test void missingRequiredParamIs400Not500() throws Exception {
    mvc.perform(get("/api/public/departments"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test void verifyEmailMissingTokenIs400Not500() throws Exception {
    mvc.perform(get("/api/auth/verify-email"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test void malformedJsonIs400Not500() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test void missingBodyIs400Not500() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test void pathTypeMismatchIs400Not500() throws Exception {
    mvc.perform(get("/api/profiles/abc").with(user(student())))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test void unknownApiPathIs404JsonNot500() throws Exception {
    mvc.perform(get("/api/no-such-path").with(user(student())))
      .andExpect(status().isNotFound())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test void wrongMethodIs405Not500() throws Exception {
    // DELETE needs a CSRF token to reach the dispatcher; without it the
    // CsrfFilter answers 403 first, which is correct filter behaviour.
    mvc.perform(delete("/api/health").with(csrf()))
      .andExpect(status().isMethodNotAllowed())
      .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test void nullEventIdCreatingTeamIs400Not500() throws Exception {
    mvc.perform(post("/api/teams").with(user(student())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test void nullReactionTargetIs400Not200JunkRow() throws Exception {
    mvc.perform(post("/api/reactions").with(user(student())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_TARGET"));
  }

  @Test void invalidReactionValueIs400() throws Exception {
    mvc.perform(post("/api/reactions").param("reaction", "BOGUS").with(user(student())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"POST\",\"targetId\":1}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REACTION"));
  }

  @Test void nullSkillNameIs400Not200JunkRow() throws Exception {
    mvc.perform(post("/api/profiles/me/skills").with(user(student())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("SKILL_NAME_REQUIRED"));
  }

  @Test void nullProjectTitleIs400Not200JunkRow() throws Exception {
    mvc.perform(post("/api/profiles/me/projects").with(user(student())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("TITLE_REQUIRED"));
  }

  @Test void nullEducationInstitutionIs400Not200JunkRow() throws Exception {
    mvc.perform(post("/api/profiles/me/education").with(user(student())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"degree\":\"BSc\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INSTITUTION_REQUIRED"));
  }

  @Test void nullAchievementTitleIs400Not200JunkRow() throws Exception {
    mvc.perform(post("/api/profiles/me/achievements").with(user(student())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("TITLE_REQUIRED"));
  }

  @Test void validCalendarDatesAre200() throws Exception {
    mvc.perform(get("/api/calendar").param("from", "2026-11-01").param("to", "2026-12-01").with(user(student())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$").isArray());
  }

  @Test void constraintViolationListsFieldErrors() {
    var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    var violations = validator.validate(new com.unilink.identity.dto.AuthDtos.LoginRequest(null, "x"));
    org.junit.jupiter.api.Assertions.assertFalse(violations.isEmpty());
    var handler = new GlobalExceptionHandler();
    var req = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/auth/login");
    var resp = handler.cv(new jakarta.validation.ConstraintViolationException(violations), req);
    org.junit.jupiter.api.Assertions.assertEquals(422, resp.getBody().getStatus());
    org.junit.jupiter.api.Assertions.assertFalse(resp.getBody().getFieldErrors().isEmpty());
    org.junit.jupiter.api.Assertions.assertEquals("email", resp.getBody().getFieldErrors().get(0).field());
  }

  @Test void negativeNotificationLimitIs400Not500() throws Exception {
    mvc.perform(get("/api/notifications").param("limit", "-1").with(user(student())))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test void badCalendarDatesAre400Not500() throws Exception {
    // Pre-fix the endpoint answered 200 with silently wrong results: the
    // unvalidated strings were passed straight to the SQL comparison, so
    // garbage dates were coerced instead of rejected.
    mvc.perform(get("/api/calendar").param("from", "not-a-date").param("to", "also-bad").with(user(student())))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test void nullReportTargetIs400Not500() throws Exception {
    // A reason is supplied so the request passes the reason check and
    // reaches resolveOwner's switch on the null targetType (pre-fix: NPE 500).
    mvc.perform(post("/api/reports").with(user(student())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":null,\"targetId\":null,\"reason\":\"spam\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_TARGET"));
  }

  @Test void nullModerationDecideTargetIs400Not500() throws Exception {
    // Reason + action supplied so the request reaches the switch on the
    // null targetType (pre-fix: NPE 500).
    mvc.perform(post("/api/admin/moderation/decide").with(user(admin())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":null,\"targetId\":null,\"action\":\"HIDE\",\"reason\":\"spam\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_TARGET"));
  }

  @Test void studentLoginWrongPasswordIs401() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever123456\"}"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test void adminLoginWrongPasswordIs401() throws Exception {
    mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever123456\"}"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test void unauthenticatedStudentApiIs401JsonNotHtml() throws Exception {
    mvc.perform(get("/api/feed"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test void unauthenticatedAdminApiIs401JsonNotHtml() throws Exception {
    mvc.perform(get("/api/admin/dashboard"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test void forbiddenAdminApiIs403JsonNotHtml() throws Exception {
    // Authenticated student principal, no MFA_VERIFIED authority: the admin
    // chain's access-denied handler must answer ApiError JSON, not sendError.
    mvc.perform(get("/api/admin/dashboard").with(user(student())))
      .andExpect(status().isForbidden())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }
}
