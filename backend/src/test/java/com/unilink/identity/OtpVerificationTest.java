package com.unilink.identity;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.PasswordResetTokenRepository;
import com.unilink.identity.repo.PhoneOtpRepository;
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
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** OTP verification semantics (2026-11-24): expired codes are rejected,
 *  a correct code works exactly once, and the same holds for password
 *  reset codes. Incorrect-code caps already live in OtpRateLimitTest. */
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
class OtpVerificationTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @Autowired PhoneOtpRepository phoneOtps;
  @Autowired PasswordResetTokenRepository resetTokens;
  @Autowired LoginThrottle throttle;
  @BeforeEach void reset(){ TestProviders.reset(); throttle.resetForTests(); }

  private User unverifiedUser(String tag){
    var f = factory.seedMaster();
    User u = factory.verifiedStudent("ov-" + tag + "-" + System.nanoTime() + "@example.com",
      "OV-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    u.setState("REGISTERED"); u.setPhoneVerifiedAt(null); u.setEmailVerifiedAt(null);
    return users.save(u);
  }

  private String phoneOtpFor(User u) throws Exception {
    mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + "}"))
      .andExpect(status().isOk());
    String otp = TestProviders.SMS_OTPS.get(u.getPhone());
    assertNotNull(otp, "resend must have sent an OTP");
    return otp;
  }

  @Test void expiredPhoneOtpIsRejected() throws Exception {
    User u = unverifiedUser("expired");
    String otp = phoneOtpFor(u);
    var row = phoneOtps.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(u.getId()).orElseThrow();
    row.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    phoneOtps.save(row);
    mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + ",\"otp\":\"" + otp + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
  }

  @Test void phoneOtpWorksExactlyOnce() throws Exception {
    User u = unverifiedUser("reuse");
    String otp = phoneOtpFor(u);
    mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + ",\"otp\":\"" + otp + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.phoneVerified").value(true));
    mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + ",\"otp\":\"" + otp + "\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_NOT_FOUND"));
  }

  private String resetCodeFor(User u) throws Exception {
    mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\"}"))
      .andExpect(status().isOk());
    String otp = TestProviders.EMAIL_OTPS.get(u.getEmail());
    assertNotNull(otp, "reset request must have sent a code");
    return otp;
  }

  @Test void expiredResetCodeIsRejected() throws Exception {
    User u = unverifiedUser("reset-expired");
    String otp = resetCodeFor(u);
    var row = resetTokens.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(u.getId()).orElseThrow();
    row.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    resetTokens.save(row);
    mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"otp\":\"" + otp + "\",\"newPassword\":\"NewPassword1\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
  }

  @Test void resetCodeWorksExactlyOnceAndNewPasswordLogsIn() throws Exception {
    User u = unverifiedUser("reset-reuse");
    String otp = resetCodeFor(u);
    mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"otp\":\"" + otp + "\",\"newPassword\":\"NewPassword1\"}"))
      .andExpect(status().isOk());
    mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"otp\":\"" + otp + "\",\"newPassword\":\"NewPassword2\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("OTP_NOT_FOUND"));
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"NewPassword1\"}"))
      .andExpect(status().isOk());
  }
}
