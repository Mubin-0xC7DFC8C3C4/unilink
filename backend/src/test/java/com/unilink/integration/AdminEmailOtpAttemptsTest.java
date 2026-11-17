package com.unilink.integration;

import com.unilink.admin.AdminAuthService;
import com.unilink.common.Exceptions;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.security.LoginThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** Admin email-OTP wrong-guess counter (D-117; same D-103 bug class as the
 *  phone and password-reset OTPs). Deliberately NOT @Transactional: the
 *  existing AdminAuthSecurityTest.emailOtpAttemptCounterSurvivesFreshRequest
 *  passes despite the bug, because inside one @Transactional test the
 *  persistence context still shows the rolled-back increment. Here every
 *  service call runs in its own transaction, as in production, so a
 *  setAttempts+save that rolls back with OTP_INVALID would leave
 *  attempts == 0 and the 5-attempt cap would never engage. */
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
class AdminEmailOtpAttemptsTest {
  @Autowired TestDataFactory factory;
  @Autowired AdminAuthService auth;
  @Autowired AdminEmailOtpRepository emailOtps;
  @Autowired LoginThrottle throttle;

  @BeforeEach void setup() {
    throttle.resetForTests();
    TestProviders.reset();
  }

  private AdminAccount moderator() {
    return factory.admin("emailotp-" + System.nanoTime() + "@example.com",
      "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
  }

  private static String wrongCode(String valid) { return "000000".equals(valid) ? "000001" : "000000"; }

  private <T extends Throwable> T assertCode(Class<T> type, String code, org.junit.jupiter.api.function.Executable exec) {
    T t = assertThrows(type, exec);
    assertEquals(code, ((Exceptions.AppException) t).code());
    return t;
  }

  @Test void wrongGuessesAreCountedAcrossTransactions() {
    AdminAccount a = moderator();
    auth.requestEmailOtp(a.getId());
    String real = TestProviders.EMAIL_OTPS.get(a.getEmail());
    assertNotNull(real, "fake email provider must have recorded the OTP");
    String wrong = wrongCode(real);
    for (int i = 1; i <= 3; i++) {
      int expected = i;
      assertCode(Exceptions.BadRequest.class, "OTP_INVALID",
        () -> auth.verifyEmailOtp(a.getId(), wrong, "127.0.0.1"));
      // Re-read from the database: this only passes if the increment
      // survived the verify call's rolled-back transaction.
      AdminEmailOtp row = emailOtps.findTopByAdminIdAndUsedFalseOrderByCreatedAtDesc(a.getId()).orElseThrow();
      assertEquals(expected, row.getAttempts(), "attempts must persist after wrong guess " + i);
    }
  }

  @Test void sixthWrongGuessHitsAttemptCap() {
    AdminAccount a = moderator();
    auth.requestEmailOtp(a.getId());
    String real = TestProviders.EMAIL_OTPS.get(a.getEmail());
    String wrong = wrongCode(real);
    for (int i = 0; i < 5; i++)
      assertCode(Exceptions.BadRequest.class, "OTP_INVALID",
        () -> auth.verifyEmailOtp(a.getId(), wrong, "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "OTP_ATTEMPTS_EXCEEDED",
      () -> auth.verifyEmailOtp(a.getId(), wrong, "127.0.0.1"));
  }

  @Test void correctCodeStillVerifiesAfterWrongGuesses() {
    AdminAccount a = moderator();
    auth.requestEmailOtp(a.getId());
    String real = TestProviders.EMAIL_OTPS.get(a.getEmail());
    assertNotNull(real);
    assertCode(Exceptions.BadRequest.class, "OTP_INVALID",
      () -> auth.verifyEmailOtp(a.getId(), wrongCode(real), "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "OTP_INVALID",
      () -> auth.verifyEmailOtp(a.getId(), wrongCode(real), "127.0.0.1"));
    assertNotNull(auth.verifyEmailOtp(a.getId(), real, "127.0.0.1"));
    AdminEmailOtp row = emailOtps.findTopByAdminIdAndUsedFalseOrderByCreatedAtDesc(a.getId()).orElse(null);
    assertNull(row, "a verified OTP must be marked used, so no live row remains");
  }
}
