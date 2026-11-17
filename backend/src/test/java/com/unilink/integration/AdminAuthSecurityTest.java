package com.unilink.integration;

import com.unilink.admin.AdminAuthService;
import com.unilink.common.Exceptions;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.security.AesGcm;
import com.unilink.security.LoginThrottle;
import com.unilink.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Admin TOTP / email-OTP rate limits, null-code handling and re-enrolment
 *  guard (fixes 4, 5, 6). Service-level tests on H2; the fake email provider
 *  records sent OTPs in TestProviders.EMAIL_OTPS. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-adminauth;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-adminauth-key"
})
@Transactional
class AdminAuthSecurityTest {
  @Autowired TestDataFactory factory;
  @Autowired AdminAuthService auth;
  @Autowired AdminAccountRepository admins;
  @Autowired AdminTotpSecretRepository totpSecrets;
  @Autowired TotpService totp;
  @Autowired LoginThrottle throttle;

  /** Must match the app.encryption-key override above. */
  private static final String ENCRYPTION_KEY = "test-adminauth-key";
  @BeforeEach void setup() {
    throttle.resetForTests();
    TestProviders.reset();
  }

  private AdminAccount adminWithSecret(boolean enrolled) {
    AdminAccount a = factory.admin("authsec-" + System.nanoTime() + "@example.com",
      "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
    String secret = totp.generateSecret();
    AdminTotpSecret row = new AdminTotpSecret();
    row.setId(a.getId());
    row.setSecretEncrypted(AesGcm.encrypt(ENCRYPTION_KEY, secret));
    totpSecrets.save(row);
    a.setTotpEnrolled(enrolled);
    return admins.save(a);
  }
  private String secretFor(Long adminId) {
    String enc = totpSecrets.findById(adminId).orElseThrow().getSecretEncrypted();
    return AesGcm.decrypt(ENCRYPTION_KEY, enc);
  }
  private String validCode(String secret) throws Exception {
    Method gen = TotpService.class.getDeclaredMethod("generate", String.class, long.class);
    gen.setAccessible(true);
    long step = java.time.Instant.now().getEpochSecond() / 30;
    return (String) gen.invoke(totp, secret, step);
  }
  private static String wrongCode(String valid) { return "000000".equals(valid) ? "000001" : "000000"; }
  private <T extends Throwable> T assertCode(Class<T> type, String code, Runnable r) {
    T e = assertThrows(type, r::run);
    assertEquals(code, ((Exceptions.AppException) e).code());
    return e;
  }

  // ---- Fix 4: TOTP verify locks after 5 failures ----
  @Test void totpVerifyLocksAfterFiveFailures() throws Exception {
    AdminAccount a = adminWithSecret(true);
    String wrong = wrongCode(validCode(secretFor(a.getId())));
    for (int i = 0; i < 5; i++)
      assertCode(Exceptions.BadRequest.class, "INVALID_TOTP",
        () -> auth.verifyTotpOrRecovery(a.getId(), wrong, "127.0.0.1"));
    assertThrows(Exceptions.TooManyRequests.class,
      () -> auth.verifyTotpOrRecovery(a.getId(), wrong, "127.0.0.1"));
  }
  @Test void totpEnrolLocksAfterFiveFailures() throws Exception {
    AdminAccount a = adminWithSecret(false);
    String wrong = wrongCode(validCode(secretFor(a.getId())));
    for (int i = 0; i < 5; i++)
      assertCode(Exceptions.BadRequest.class, "INVALID_TOTP",
        () -> auth.enrolTotp(a.getId(), wrong, "127.0.0.1"));
    assertThrows(Exceptions.TooManyRequests.class,
      () -> auth.enrolTotp(a.getId(), wrong, "127.0.0.1"));
  }
  @Test void emailOtpRequestLocksAfterFiveRequests() {
    AdminAccount a = adminWithSecret(true);
    for (int i = 0; i < 5; i++) auth.requestEmailOtp(a.getId());
    assertThrows(Exceptions.TooManyRequests.class, () -> auth.requestEmailOtp(a.getId()));
  }
  @Test void emailOtpAttemptCounterSurvivesFreshRequest() {
    AdminAccount a = adminWithSecret(true);
    auth.requestEmailOtp(a.getId());
    String real = TestProviders.EMAIL_OTPS.get(a.getEmail());
    assertNotNull(real, "fake email provider must have recorded the OTP");
    String wrong = wrongCode(real);
    for (int i = 0; i < 3; i++)
      assertCode(Exceptions.BadRequest.class, "OTP_INVALID",
        () -> auth.verifyEmailOtp(a.getId(), wrong, "127.0.0.1"));
    // Fresh OTP request must NOT reset the 3 attempts already made.
    auth.requestEmailOtp(a.getId());
    assertCode(Exceptions.BadRequest.class, "OTP_INVALID",
      () -> auth.verifyEmailOtp(a.getId(), wrong, "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "OTP_INVALID",
      () -> auth.verifyEmailOtp(a.getId(), wrong, "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "OTP_ATTEMPTS_EXCEEDED",
      () -> auth.verifyEmailOtp(a.getId(), wrong, "127.0.0.1"));
  }

  // ---- Fix 5: null / blank codes are 400, not 500 ----
  @Test void nullCodesAre400Not500() {
    AdminAccount a = adminWithSecret(true);
    assertCode(Exceptions.BadRequest.class, "CODE_REQUIRED", () -> auth.enrolTotp(a.getId(), null, "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "CODE_REQUIRED", () -> auth.verifyTotpOrRecovery(a.getId(), null, "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "CODE_REQUIRED", () -> auth.verifyEmailOtp(a.getId(), null, "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "CODE_REQUIRED", () -> auth.verifyEmailOtp(a.getId(), "  ", "127.0.0.1"));
  }

  // ---- Fix 6: no re-enrolment when already enrolled; normal flow works ----
  @Test void enrolVerifyFlowWorksAndReEnrolmentIsRejected() throws Exception {
    AdminAccount a = adminWithSecret(false);
    String secret = secretFor(a.getId());
    List<String> codes = auth.enrolTotp(a.getId(), validCode(secret), "127.0.0.1");
    assertEquals(10, codes.size());
    assertEquals(true, admins.findById(a.getId()).orElseThrow().getTotpEnrolled());
    var principal = auth.verifyTotpOrRecovery(a.getId(), validCode(secret), "127.0.0.1");
    assertTrue(principal.mfaVerified());
    String freshCode = validCode(secret);
    assertCode(Exceptions.BadRequest.class, "ALREADY_ENROLLED",
      () -> auth.enrolTotp(a.getId(), freshCode, "127.0.0.1"));
  }
}
