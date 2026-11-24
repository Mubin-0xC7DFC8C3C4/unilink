package com.unilink.identity;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.UserRepository;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
import com.unilink.security.LoginThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Email verification link resend (2026-11-24, real SMTP): the secure
 *  retry when the registration send failed or the link expired. Same
 *  anti-enumeration and rate-limit contract as the phone OTP resend, and
 *  re-issuing retires the previous link. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class EmailVerificationResendTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @Autowired LoginThrottle throttle;
  @BeforeEach void reset(){ TestProviders.reset(); throttle.resetForTests(); }

  private User unverifiedUser(String tag){
    var f = factory.seedMaster();
    User u = factory.verifiedStudent("evr-" + tag + "-" + System.nanoTime() + "@example.com",
      "EVR-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    u.setState("REGISTERED"); u.setPhoneVerifiedAt(null); u.setEmailVerifiedAt(null);
    return users.save(u);
  }

  private static String tokenFromLink(String link){
    int i = link.indexOf("token=");
    assertTrue(i > 0, "link must carry a token: " + link);
    return link.substring(i + 6);
  }

  @Test void resendByUserIdThenByEmailRetiresPreviousLink() throws Exception {
    User u = unverifiedUser("fresh");
    mvc.perform(post("/api/auth/email-verification/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + "}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("Verification email resent"));
    String firstLink = TestProviders.EMAIL_LINKS.get(u.getEmail());
    assertNotNull(firstLink, "first resend must send a link");
    String firstToken = tokenFromLink(firstLink);

    // Immediate repeat is rate-limited (60 s cooldown).
    mvc.perform(post("/api/auth/email-verification/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + "}"))
      .andExpect(status().isTooManyRequests());
    throttle.resetForTests();

    // Second resend (by email this time) issues a fresh link.
    mvc.perform(post("/api/auth/email-verification/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\"}"))
      .andExpect(status().isOk());
    String secondLink = TestProviders.EMAIL_LINKS.get(u.getEmail());
    assertNotNull(secondLink, "second resend must send a link");
    String secondToken = tokenFromLink(secondLink);
    assertNotEquals(firstToken, secondToken, "re-issue must mint a fresh token");

    // The old link is retired.
    mvc.perform(get("/api/auth/verify-email").param("token", firstToken))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("TOKEN_NOT_FOUND"));
    // The new link verifies, and cannot be reused.
    mvc.perform(get("/api/auth/verify-email").param("token", secondToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.emailVerified").value(true));
    mvc.perform(get("/api/auth/verify-email").param("token", secondToken))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("TOKEN_NOT_FOUND"));
  }

  @Test void resendUnknownUserReturnsGeneric200WithoutSending() throws Exception {
    mvc.perform(post("/api/auth/email-verification/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":999999}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("Verification email resent"));
    assertTrue(TestProviders.EMAIL_LINKS.isEmpty(), "no link may be sent for an unknown user");
    mvc.perform(post("/api/auth/email-verification/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"ghost-" + System.nanoTime() + "@example.com\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("Verification email resent"));
    assertTrue(TestProviders.EMAIL_LINKS.isEmpty(), "no link may be sent for an unknown email");
  }

  @Test void resendAlreadyVerifiedReturnsGeneric200WithoutSending() throws Exception {
    var f = factory.seedMaster();
    User v = factory.verifiedStudent("evr-verified-" + System.nanoTime() + "@example.com",
      "EVR-V-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    mvc.perform(post("/api/auth/email-verification/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + v.getId() + "}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("Verification email resent"));
    assertTrue(TestProviders.EMAIL_LINKS.isEmpty(), "no link may be sent for an already-verified email");
  }

  @Test void resendWithoutIdentifierIsRejected() throws Exception {
    mvc.perform(post("/api/auth/email-verification/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isBadRequest());
  }
}
