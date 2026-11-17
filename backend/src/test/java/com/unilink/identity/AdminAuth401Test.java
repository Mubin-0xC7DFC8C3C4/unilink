package com.unilink.identity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Admin-auth 401 (I-15): /api/admin/auth/** is permitAll, so an unauthenticated
 *  request reaches AdminAuthController with a null Authentication. The old code
 *  cast a.getPrincipal() unconditionally and threw NPE (500). Every endpoint
 *  that needs a principal must answer 401 instead. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AdminAuth401Test {
  @Autowired MockMvc mvc;
  @Test void unauthenticatedTotpEnrolIs401() throws Exception {
    mvc.perform(post("/api/admin/auth/totp/enrol").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
      .andExpect(status().isUnauthorized());
  }
  @Test void unauthenticatedTotpVerifyIs401() throws Exception {
    mvc.perform(post("/api/admin/auth/totp/verify").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
      .andExpect(status().isUnauthorized());
  }
  @Test void unauthenticatedEmailOtpRequestIs401() throws Exception {
    mvc.perform(post("/api/admin/auth/email-otp/request"))
      .andExpect(status().isUnauthorized());
  }
  @Test void unauthenticatedEmailOtpVerifyIs401() throws Exception {
    mvc.perform(post("/api/admin/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"123456\"}"))
      .andExpect(status().isUnauthorized());
  }
  @Test void unauthenticatedAdminMeIs401() throws Exception {
    mvc.perform(get("/api/admin/auth/me"))
      .andExpect(status().isUnauthorized());
  }
  @Test void adminLoginWithUnknownEmailIs400Not500() throws Exception {
    mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever123456\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }
}
