package com.unilink.identity;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.UserRepository;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** OTP resend (I-5) + register phone validation at the HTTP layer (I-6).
 *  Runs against the H2 context-test setup (same properties as
 *  ApplicationContextTest); the fake SMS provider records the sent OTP. */
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
class OtpResendTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @BeforeEach void reset(){ TestProviders.reset(); }
  private User registeredStudent(String tag){
    var f = factory.seedMaster();
    User u = factory.verifiedStudent("otp-" + tag + "-" + System.nanoTime() + "@example.com",
      "OTP-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    u.setState("REGISTERED"); u.setPhoneVerifiedAt(null); u.setEmailVerifiedAt(null);
    return users.save(u);
  }
  @Test void resendSendsNewOtpThenCooldownBlocksImmediateRepeat() throws Exception {
    User u = registeredStudent("cool");
    mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + "}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("OTP resent"));
    assertTrue(TestProviders.SMS_OTPS.containsKey(u.getPhone()), "OTP must be sent to the user's phone");
    mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + "}"))
      .andExpect(status().isTooManyRequests());
  }
  /** Fix 7: unknown user / already-verified phone get the same generic 200
   *  as a real resend, and no OTP is sent — the endpoint leaks neither
   *  existence nor verification state. */
  @Test void resendUnknownUserReturnsGeneric200WithoutSending() throws Exception {
    mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":999999}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("OTP resent"));
    assertTrue(TestProviders.SMS_OTPS.isEmpty(), "no OTP may be sent for an unknown user");
  }
  @Test void resendAlreadyVerifiedPhoneReturnsGeneric200WithoutSending() throws Exception {
    var f = factory.seedMaster();
    User v = factory.verifiedStudent("otp-verified-" + System.nanoTime() + "@example.com",
      "OTP-V-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + v.getId() + "}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("OTP resent"));
    assertTrue(TestProviders.SMS_OTPS.isEmpty(), "no OTP may be sent for an already-verified phone");
  }
  /** D-173 (owner brief 2026-11-25): registration no longer sends a phone
   *  OTP — the journey is OCR → email OTP code — so there is no cooldown
   *  to seed. The legacy phone flow is still available on demand: a
   *  resend right after sign-up succeeds and sends a code. */
  @Test void registerSendsNoPhoneOtpButResendWorks() throws Exception {
    var f = factory.seedMaster();
    String email = "otp-reg-" + System.nanoTime() + "@example.com";
    // Real PNG magic bytes: UploadValidator sniffs content, junk bytes are rejected.
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};
    MockMultipartFile idCard = new MockMultipartFile("idCard", "id.jpg", "image/jpeg", png);
    String body = mvc.perform(multipart("/api/auth/register").file(idCard)
        .param("fullName", "Test User").param("phone", "01712345678").param("email", email)
        .param("universityId", f.mu().getId().toString()).param("departmentId", f.muCse().getId().toString())
        .param("address", "123 Test Road").param("studentId", "CSE-" + System.nanoTime()).param("password", "Password1"))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    assertTrue(TestProviders.SMS_OTPS.isEmpty(), "registration must not send a phone OTP");
    long userId = Long.parseLong(body.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + userId + "}"))
      .andExpect(status().isOk());
    assertTrue(TestProviders.SMS_OTPS.containsKey("01712345678"), "the resend must send a phone OTP on demand");
  }
  @Test void registerRejectsBadPhoneWith422() throws Exception {
    var f = factory.seedMaster();
    MockMultipartFile idCard = new MockMultipartFile("idCard", "id.jpg", "image/jpeg", new byte[]{1, 2, 3});
    MockMultipartFile photo = new MockMultipartFile("profilePhoto", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    mvc.perform(multipart("/api/auth/register").file(idCard).file(photo)
        .param("fullName", "Test User").param("phone", "01234567890").param("email", "bad-phone@example.com")
        .param("universityId", f.mu().getId().toString()).param("departmentId", f.muCse().getId().toString())
        .param("address", "123 Test Road").param("studentId", "CSE-999").param("password", "Password1"))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
