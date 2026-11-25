package com.unilink.identity;

import com.unilink.identity.repo.EmailOtpRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Registration journey (D-173/D-174/D-175/D-176): register without a
 *  profile photo → registration token → OCR auto-verifies (fake provider)
 *  → email OTP code is sent → wrong / expired / exhausted codes fail →
 *  the right code activates the account → login works. No phone OTP and
 *  no email link are sent at registration any more. */
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
class RegistrationVerificationFlowTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @Autowired EmailOtpRepository emailOtps;
  @Autowired LoginThrottle throttle;
  @BeforeEach void reset(){ TestProviders.reset(); throttle.resetForTests(); }

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};

  /** Registers without a profile photo; returns the registration token. */
  private String register(String tag, String email) throws Exception {
    var f = factory.seedMaster();
    MockMultipartFile idCard = new MockMultipartFile("idCard", "id.jpg", "image/jpeg", PNG);
    MvcResult result = mvc.perform(multipart("/api/auth/register").file(idCard)
        .param("fullName", "Tanvir Mahmud").param("phone", "01712345678").param("email", email)
        .param("universityId", f.mu().getId().toString()).param("departmentId", f.muCse().getId().toString())
        .param("batchId", f.muBatch().getId().toString())
        .param("address", "House 12, Road 5, Dhanmondi, Dhaka")
        .param("studentId", "RV-" + tag + "-" + System.nanoTime()).param("password", "Password1"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").exists())
      .andExpect(jsonPath("$.verificationToken").exists())
      .andReturn();
    String body = result.getResponse().getContentAsString();
    String token = body.replaceAll(".*\"verificationToken\"\\s*:\\s*\"([0-9a-f]+)\".*", "$1");
    assertFalse(token.equals(body), "the register response must carry the registration token");
    assertTrue(TestProviders.SMS_OTPS.isEmpty(), "registration must not send a phone OTP any more");
    assertTrue(TestProviders.EMAIL_LINKS.isEmpty(), "registration must not send an email link any more");
    return token;
  }
  /** Polls the token-scoped status until OCR leaves PENDING (the fake
   *  provider answers instantly, but the job runs on another thread). */
  private String waitForOcr(String token) throws Exception {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline){
      MvcResult r = mvc.perform(get("/api/auth/registration/status").param("token", token))
          .andExpect(status().isOk()).andReturn();
      String body = r.getResponse().getContentAsString();
      if (!body.contains("\"ocrStatus\":\"PENDING\"")) return body;
      Thread.sleep(100);
    }
    fail("OCR did not finish within 10 s");
    return null;
  }
  private String emailCode(String email) throws Exception {
    // D-188 (2026-11-25): the OCR job persists AUTO_VERIFIED in one
    // transaction and sends the email OTP from its own transaction, so
    // the code can legitimately appear a moment after the status leaves
    // PENDING. Poll for it instead of asserting it is already there.
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline){
      String code = TestProviders.EMAIL_OTPS.get(email);
      if (code != null) return code;
      Thread.sleep(100);
    }
    fail("an email OTP code must have been sent after AUTO_VERIFIED");
    return null;
  }
  /** Polls the token-scoped status until the email OTP has been sent.
   *  D-188: same two-transaction window as emailCode — AUTO_VERIFIED can
   *  be visible before the OTP row exists, so the flag must be awaited. */
  private String waitForEmailOtpSent(String token) throws Exception {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline){
      MvcResult r = mvc.perform(get("/api/auth/registration/status").param("token", token))
          .andExpect(status().isOk()).andReturn();
      String body = r.getResponse().getContentAsString();
      if (body.contains("\"emailOtpSent\":true")) return body;
      Thread.sleep(100);
    }
    fail("the email OTP was not sent within 10 s of AUTO_VERIFIED");
    return null;
  }

  @Test void fullJourneyActivatesTheAccount() throws Exception {
    String email = "rv-full-" + System.nanoTime() + "@example.com";
    String token = register("full", email);
    String status = waitForOcr(token);
    assertTrue(status.contains("\"ocrStatus\":\"AUTO_VERIFIED\""), "fake OCR must auto-verify; status was " + status);
    status = waitForEmailOtpSent(token);
    assertTrue(status.contains("\"emailOtpSent\":true"), "the email OTP must have been sent; status was " + status);

    String code = emailCode(email);
    String wrong = "000000".equals(code) ? "111111" : "000000";
    mvc.perform(post("/api/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"otp\":\"" + wrong + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    mvc.perform(post("/api/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"otp\":\"" + code + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.emailVerified").value(true))
      .andExpect(jsonPath("$.state").value("VERIFIED"));
    assertEquals("VERIFIED", users.findByEmail(email).orElseThrow().getState());

    // The activated account can log in with the registered password.
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"Password1\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.state").value("VERIFIED"));

    // The status endpoint now reports the completed journey.
    mvc.perform(get("/api/auth/registration/status").param("token", token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.emailVerified").value(true))
      .andExpect(jsonPath("$.ocrStatus").value("AUTO_VERIFIED"));
    // And the code is single-use.
    mvc.perform(post("/api/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"otp\":\"" + code + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_NOT_FOUND"));
  }
  @Test void registrationStillAcceptsAnOptionalProfilePhoto() throws Exception {
    var f = factory.seedMaster();
    MockMultipartFile idCard = new MockMultipartFile("idCard", "id.jpg", "image/jpeg", PNG);
    MockMultipartFile photo = new MockMultipartFile("profilePhoto", "photo.jpg", "image/jpeg", PNG);
    mvc.perform(multipart("/api/auth/register").file(idCard).file(photo)
        .param("fullName", "Photo User").param("phone", "01712345679").param("email", "rv-photo-" + System.nanoTime() + "@example.com")
        .param("universityId", f.mu().getId().toString()).param("departmentId", f.muCse().getId().toString())
        .param("address", "Dhanmondi, Dhaka").param("studentId", "RV-PHOTO-" + System.nanoTime()).param("password", "Password1"))
      .andExpect(status().isCreated());
  }
  @Test void unknownTokenIsNotFoundAndResendNeedsAutoVerified() throws Exception {
    mvc.perform(get("/api/auth/registration/status").param("token", "no-such-token"))
      .andExpect(status().isNotFound());
    mvc.perform(post("/api/auth/email-otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"no-such-token\"}"))
      .andExpect(status().isNotFound());
    mvc.perform(post("/api/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"no-such-token\",\"otp\":\"123456\"}"))
      .andExpect(status().isNotFound());
  }
  @Test void resendCooldownAndExpiryAndAttemptCap() throws Exception {
    String email = "rv-resend-" + System.nanoTime() + "@example.com";
    String token = register("resend", email);
    waitForOcr(token);
    String first = emailCode(email);
    // First resend succeeds and retires the first code.
    mvc.perform(post("/api/auth/email-otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\"}"))
      .andExpect(status().isOk());
    String second = TestProviders.EMAIL_OTPS.get(email);
    assertNotEquals(first, second, "a resend must issue a fresh code");
    // Immediate repeat hits the 60 s cooldown.
    mvc.perform(post("/api/auth/email-otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\"}"))
      .andExpect(status().isTooManyRequests());
    // The retired code no longer works.
    mvc.perform(post("/api/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"otp\":\"" + first + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    // Expire the live code: it is rejected as expired.
    var row = emailOtps.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(
        users.findByEmail(email).orElseThrow().getId()).orElseThrow();
    row.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    emailOtps.save(row);
    mvc.perform(post("/api/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"otp\":\"" + second + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
  }
  @Test void fiveWrongGuessesExhaustTheCode() throws Exception {
    String email = "rv-cap-" + System.nanoTime() + "@example.com";
    String token = register("cap", email);
    waitForOcr(token);
    String code = emailCode(email);
    String wrong = "000000".equals(code) ? "111111" : "000000";
    for (int i = 0; i < 5; i++)
      mvc.perform(post("/api/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON)
          .content("{\"token\":\"" + token + "\",\"otp\":\"" + wrong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    mvc.perform(post("/api/auth/email-otp/verify").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"otp\":\"" + code + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
  }
}
