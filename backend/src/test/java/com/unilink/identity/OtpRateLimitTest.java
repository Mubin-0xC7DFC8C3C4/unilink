package com.unilink.identity;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.UserRepository;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
import com.unilink.security.LoginThrottle;
import com.unilink.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.lang.reflect.Method;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** OTP / login / TOTP rate limits and anti-enumeration at the HTTP layer
 *  (Phase 3 Worker B: D-099–D-102). Runs against the H2 context-test
 *  setup; the fake SMS/email providers record sent codes. The throttle is
 *  reset before every test so caps are measured from zero. */
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
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "spring.session.jdbc.initialize-schema=always"
})
class OtpRateLimitTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @Autowired LoginThrottle throttle;
  @Autowired TotpService totp;
  @BeforeEach void reset(){ TestProviders.reset(); throttle.resetForTests(); }

  private User unverifiedUser(String tag, String phone){
    var f = factory.seedMaster();
    User u = factory.verifiedStudent("rl-" + tag + "-" + System.nanoTime() + "@example.com",
      "RL-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    u.setState("REGISTERED"); u.setPhoneVerifiedAt(null); u.setEmailVerifiedAt(null); u.setPhone(phone);
    return users.save(u);
  }
  private MvcResult resend(User u) throws Exception {
    return mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + "}")).andReturn();
  }
  private MvcResult resendAs(User u, String remote, String xff) throws Exception {
    return mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + "}")
        .with(req -> { req.setRemoteAddr(remote); if (xff != null) req.addHeader("X-Forwarded-For", xff); return req; }))
      .andReturn();
  }

  /** D-099: five resends across accounts sharing one phone exhaust the
   *  per-phone cap (5/hour); the sixth account's resend is 429 even
   *  though its own per-user quota and the shared IP quota are untouched. */
  @Test void resendPerPhoneCapBlocksSixthAccountSharingPhone() throws Exception {
    String phone = "01910000001";
    User[] us = new User[6];
    for (int i = 0; i < 6; i++) us[i] = unverifiedUser("phone" + i, phone);
    for (int i = 0; i < 5; i++)
      assertEquals(200, resend(us[i]).getResponse().getStatus(), "resend " + (i + 1) + " must succeed");
    String otpAfterFive = TestProviders.SMS_OTPS.get(phone);
    assertNotNull(otpAfterFive, "5 OTPs must have been sent to the shared phone");
    assertEquals(429, resend(us[5]).getResponse().getStatus(),
      "6th resend to the same phone must be rate-limited");
    assertEquals(otpAfterFive, TestProviders.SMS_OTPS.get(phone),
      "the rate-limited 6th resend must not have sent another OTP");
  }

  /** D-100 (trusted proxy): the forwarded client IP is the rate-limit key,
   *  so 11 resends from 11 distinct forwarded IPs all succeed. */
  @Test void resendFromTrustedProxyUsesForwardedIp() throws Exception {
    for (int i = 0; i < 11; i++){
      User u = unverifiedUser("xff-ok" + i, "019200000" + String.format("%02d", i));
      assertEquals(200, resendAs(u, "127.0.0.1", "198.51.100." + (i + 1)).getResponse().getStatus(),
        "resend " + (i + 1) + " from a distinct forwarded IP must succeed");
    }
  }

  /** D-100 (untrusted proxy): a spoofed X-Forwarded-For is ignored, so
   *  11 resends that vary only the spoofed header still share the direct
   *  peer's 10/hour IP cap — the 11th is 429. */
  @Test void resendFromUntrustedProxyIgnoresSpoofedForwardedIp() throws Exception {
    for (int i = 0; i < 10; i++){
      User u = unverifiedUser("xff-bad" + i, "019300000" + String.format("%02d", i));
      assertEquals(200, resendAs(u, "203.0.113.9", "198.51.100." + (i + 1)).getResponse().getStatus(),
        "resend " + (i + 1) + " must succeed");
    }
    User last = unverifiedUser("xff-bad10", "01930000099");
    assertEquals(429, resendAs(last, "203.0.113.9", "198.51.100.99").getResponse().getStatus(),
      "11th resend from the same direct peer must hit the per-IP cap despite the spoofed header");
  }

  /** Login throttle (FR-AUTH-10): 5 failures lock the account for 15
   *  minutes; the 6th attempt — even with the right password — is 429. */
  @Test void loginLocksAfterFiveFailures() throws Exception {
    User u = unverifiedUser("lock", "01940000001");
    String email = u.getEmail();
    for (int i = 0; i < 5; i++)
      mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"" + email + "\",\"password\":\"WrongPass1\"}"))
        // D-139: was 400; a failed sign-in is 401.
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"WrongPass1\"}"))
      .andExpect(status().isTooManyRequests());
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"Student123\"}"))
      .andExpect(status().isTooManyRequests());
  }

  /** Enumeration: a login for an unknown email is indistinguishable from
   *  a wrong password for a known email (same status, code and message).
   *  The bodies also carry a per-request timestamp and traceId, so only
   *  the attacker-visible fields are compared. */
  @Test void loginErrorIsIdenticalForUnknownEmail() throws Exception {
    User u = unverifiedUser("enum", "01940000002");
    MvcResult known = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"WrongPass1\"}"))
      // D-139: was 400; a failed sign-in is 401.
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
      .andExpect(jsonPath("$.message").value("Email or password is incorrect"))
      .andReturn();
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"nobody-" + System.nanoTime() + "@example.com\",\"password\":\"WrongPass1\"}"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
      .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    assertNotNull(known);
  }

  /** Brute force on the phone OTP: 5 wrong guesses are 400 OTP_INVALID;
   *  the 6th is OTP_ATTEMPTS_EXCEEDED, and even the correct code is then
   *  refused until a new OTP is requested. */
  @Test void wrongOtpGuessesAreCapped() throws Exception {
    User u = unverifiedUser("otpbf", "01950000001");
    assertEquals(200, resend(u).getResponse().getStatus());
    String real = TestProviders.SMS_OTPS.get(u.getPhone());
    assertNotNull(real, "resend must have sent an OTP");
    String wrong = "000000".equals(real) ? "000001" : "000000";
    for (int i = 0; i < 5; i++)
      mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON)
          .content("{\"userId\":" + u.getId() + ",\"otp\":\"" + wrong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + ",\"otp\":\"" + wrong + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + ",\"otp\":\"" + real + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
  }

  /** D-102: password-reset OTP requests are capped at 5/hour per email
   *  (and 10/hour per IP) but the cap is silent — every request still
   *  returns the same generic 200, and no 6th OTP is sent. Unknown
   *  emails get the same response with nothing sent. */
  @Test void passwordResetRequestsAreCappedSilently() throws Exception {
    User u = unverifiedUser("pwreset", "01960000001");
    String email = u.getEmail();
    for (int i = 0; i < 5; i++)
      mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"" + email + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("If the email exists, a reset code was sent"));
    String afterFive = TestProviders.EMAIL_OTPS.get(email);
    assertNotNull(afterFive, "reset OTPs must have been sent");
    mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("If the email exists, a reset code was sent"));
    // The 6th request must not have replaced the OTP (it sent nothing).
    assertEquals(afterFive, TestProviders.EMAIL_OTPS.get(email));
    mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"ghost-" + System.nanoTime() + "@example.com\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("If the email exists, a reset code was sent"));
    assertFalse(TestProviders.EMAIL_OTPS.containsKey("ghost"), "no OTP may be sent for an unknown email");
  }

  /** D-103 (password-reset side): wrong reset-code guesses are counted —
   *  5 wrong guesses, then OTP_ATTEMPTS_EXCEEDED even for the correct
   *  code. Before the fix the counter rolled back with the exception, so
   *  the cap never engaged and guessing was unbounded within the 15 min
   *  code lifetime. */
  @Test void wrongPasswordResetGuessesAreCapped() throws Exception {
    User u = unverifiedUser("pwresetbf", "01960000002");
    String email = u.getEmail();
    mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\"}"))
      .andExpect(status().isOk());
    String real = TestProviders.EMAIL_OTPS.get(email);
    assertNotNull(real, "reset request must have sent an OTP");
    String wrong = "000000".equals(real) ? "000001" : "000000";
    for (int i = 0; i < 5; i++)
      mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"" + email + "\",\"otp\":\"" + wrong + "\",\"newPassword\":\"NewPassword1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"otp\":\"" + wrong + "\",\"newPassword\":\"NewPassword1\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"otp\":\"" + real + "\",\"newPassword\":\"NewPassword1\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
  }

  /** TOTP brute force at the HTTP layer: after enrolment, 5 wrong
   *  authenticator codes are 400 INVALID_TOTP and the 6th is 429.
   *  (Service-level proof already exists in AdminAuthSecurityTest; this
   *  proves the throttle is wired through the controller/session flow.) */
  @Test void totpVerifyLocksAfterFiveFailuresAtHttpLayer() throws Exception {
    var f = factory.seedMaster();
    String email = "totpbf-" + System.nanoTime() + "@example.com";
    factory.admin(email, "UNIVERSITY_MODERATOR", f.mu().getId());
    MvcResult login1 = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"Admin12345678\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.stage").value("NEEDS_ENROLMENT"))
      .andReturn();
    String body = login1.getResponse().getContentAsString();
    String secret = body.replaceAll(".*\"otpauthUrl\"\\s*:\\s*\"[^\"]*secret=([A-Z2-7]+).*", "$1");
    assertTrue(secret.matches("[A-Z2-7]+"), "otpauthUrl must carry the enrolment secret, got: " + body);
    // The session lives behind Spring Session's filter, so
    // MvcResult.getRequest().getSession() is the pre-filter request and
    // returns null; carry the session cookie like a real client instead.
    jakarta.servlet.http.Cookie session = login1.getResponse().getCookie("UL_SESSION");
    assertNotNull(session, "login must set a session cookie");
    mvc.perform(post("/api/admin/auth/totp/enrol").contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"" + validCode(secret) + "\"}").cookie(session))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("enrolled"));
    MvcResult login2 = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"Admin12345678\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.stage").value("NEEDS_TOTP"))
      .andReturn();
    jakarta.servlet.http.Cookie session2 = login2.getResponse().getCookie("UL_SESSION");
    assertNotNull(session2, "second login must set a session cookie");
    String wrong = "000000".equals(validCode(secret)) ? "000001" : "000000";
    for (int i = 0; i < 5; i++)
      mvc.perform(post("/api/admin/auth/totp/verify").contentType(MediaType.APPLICATION_JSON)
          .content("{\"code\":\"" + wrong + "\"}").cookie(session2))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOTP"));
    mvc.perform(post("/api/admin/auth/totp/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"" + wrong + "\"}").cookie(session2))
      .andExpect(status().isTooManyRequests());
  }

  /** Same reflection helper as AdminAuthSecurityTest: the current TOTP
   *  code for a secret (TotpService.generate is private). */
  private String validCode(String secret) throws Exception {
    Method gen = TotpService.class.getDeclaredMethod("generate", String.class, long.class);
    gen.setAccessible(true);
    return (String) gen.invoke(totp, secret, Instant.now().getEpochSecond() / 30);
  }
}
