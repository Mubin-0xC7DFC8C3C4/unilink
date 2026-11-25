package com.unilink.profile;

import com.unilink.common.Exceptions;
import com.unilink.identity.domain.PhoneOtp;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.PhoneOtpRepository;
import com.unilink.identity.repo.UserRepository;
import com.unilink.identity.service.OtpService;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
import com.unilink.security.LoginThrottle;
import com.unilink.security.Principals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** D-199 phone change, gateway happy path (app.sms.provider=gateway here;
 *  TestProviders' @Primary fake SMS records the code in memory instead of
 *  calling a real gateway). Deliberately NOT @Transactional — same reason
 *  as AdminEmailOtpAttemptsTest: the wrong-guess counter increments in its
 *  own transaction (D-103), which on a separate connection cannot see an
 *  OTP row that is still uncommitted inside a test transaction, so the
 *  increment would silently update zero rows and the cap could never be
 *  observed. Every service call here runs in its own transaction, as in
 *  production. Fixtures use nanoTime-unique emails/phones, so methods do
 *  not interfere. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-phonechange;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.sms.provider=gateway",
  "app.storage.local-path=./target/test-storage"
})
class PhoneChangeTest {
  @Autowired TestDataFactory factory;
  @Autowired ProfileService profiles;
  @Autowired OtpService otp;
  @Autowired UserRepository users;
  @Autowired PhoneOtpRepository phoneOtps;
  @Autowired LoginThrottle throttle;

  @BeforeEach void reset(){ TestProviders.reset(); throttle.resetForTests(); }

  private User student(String tag){
    var f = factory.seedMaster();
    return factory.verifiedStudent("pc-" + tag + "-" + System.nanoTime() + "@example.com",
      "PC-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
  }
  private static Principals.UnilinkUserPrincipal principal(User u){
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", 1L, 2L, 3L, "VERIFIED", true);
  }
  private String requestOtp(User u, String newPhone){
    profiles.requestPhoneChange(u.getId(), newPhone, "127.0.0.1");
    String code = TestProviders.SMS_OTPS.get(newPhone);
    assertNotNull(code, "an OTP must have been sent to the new number by SMS");
    return code;
  }
  private PhoneOtp newestOtp(User u){
    // No surrounding test transaction here: every read hits committed data.
    return phoneOtps.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(u.getId()).orElseThrow();
  }
  private User fresh(User u){
    return users.findById(u.getId()).orElseThrow();
  }

  @Test void correctOtpSwapsPhoneAndSetsVerifiedAt(){
    User u = student("swap");
    String oldPhone = u.getPhone();
    LocalDateTime before = LocalDateTime.now();
    String code = requestOtp(u, "01712345678");
    assertEquals("01712345678", fresh(u).getPendingPhone());
    String swapped = profiles.verifyPhoneChange(u.getId(), code);
    assertEquals("01712345678", swapped);
    User after = fresh(u);
    assertEquals("01712345678", after.getPhone());
    assertNotEquals(oldPhone, after.getPhone());
    assertNotNull(after.getPhoneVerifiedAt());
    assertFalse(after.getPhoneVerifiedAt().isBefore(before), "phoneVerifiedAt must be set to now on swap");
    assertNull(after.getPendingPhone());
    assertNull(after.getPendingPhoneRequestedAt());
    assertTrue(phoneOtps.findByUserId(u.getId()).stream().allMatch(PhoneOtp::getUsed),
      "the verifying OTP must be marked used (single-use)");
  }

  @Test void wrongOtpLeavesOldPhone(){
    User u = student("wrong");
    String code = requestOtp(u, "01712345678");
    var ex = assertThrows(Exceptions.BadRequest.class, () -> profiles.verifyPhoneChange(u.getId(), "000000"));
    assertEquals("OTP_INVALID", ex.code());
    User after = fresh(u);
    assertEquals(u.getPhone(), after.getPhone(), "a wrong code must leave the old phone in force");
    assertEquals("01712345678", after.getPendingPhone(), "the pending change survives a wrong guess");
    assertEquals(1, newestOtp(u).getAttempts(), "the wrong guess must be counted");
    // The right code still works afterwards.
    profiles.verifyPhoneChange(u.getId(), code);
    assertEquals("01712345678", fresh(u).getPhone());
  }

  @Test void expiredOtpLeavesOldPhone(){
    User u = student("expired");
    String code = requestOtp(u, "01712345678");
    var row = newestOtp(u);
    row.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    phoneOtps.save(row);
    var ex = assertThrows(Exceptions.BadRequest.class, () -> profiles.verifyPhoneChange(u.getId(), code));
    assertEquals("OTP_EXPIRED", ex.code());
    assertEquals(u.getPhone(), fresh(u).getPhone());
    assertEquals("01712345678", fresh(u).getPendingPhone());
  }

  @Test void fiveWrongAttemptsExceedsCap(){
    User u = student("attempts");
    requestOtp(u, "01712345678");
    for (int i = 0; i < 5; i++){
      assertThrows(Exceptions.BadRequest.class, () -> profiles.verifyPhoneChange(u.getId(), "000000"));
    }
    assertEquals(5, newestOtp(u).getAttempts(), "all five wrong guesses must be counted (D-103)");
    var ex = assertThrows(Exceptions.BadRequest.class, () -> profiles.verifyPhoneChange(u.getId(), "000000"));
    assertEquals("OTP_ATTEMPTS_EXCEEDED", ex.code());
    assertEquals(u.getPhone(), fresh(u).getPhone(), "the old phone survives an exhausted OTP");
  }

  @Test void otpIssuedForDifferentNumberDoesNotVerify(){
    User u = student("different");
    requestOtp(u, "01711111111"); // pending = 01711111111
    throttle.resetForTests(); // skip the 60 s cooldown for the second send
    otp.sendPhoneOtpTo(fresh(u), "01722222222", "127.0.0.1");
    String otherCode = TestProviders.SMS_OTPS.get("01722222222");
    assertNotNull(otherCode);
    var ex = assertThrows(Exceptions.BadRequest.class, () -> profiles.verifyPhoneChange(u.getId(), otherCode));
    assertEquals("OTP_INVALID", ex.code());
    assertEquals(u.getPhone(), fresh(u).getPhone());
    assertEquals("01711111111", fresh(u).getPendingPhone());
  }

  @Test void resendCooldownEnforced(){
    User u = student("cooldown");
    requestOtp(u, "01712345678");
    var ex = assertThrows(Exceptions.TooManyRequests.class,
      () -> profiles.requestPhoneChange(u.getId(), "01712345678", "127.0.0.1"));
    assertEquals("RATE_LIMITED", ex.code());
  }

  @Test void resendPerPhoneCapBlocksSixthAccountSharingPhone(){
    // D-099 shape, keyed on the NEW phone: five accounts may request a
    // change to the same number; the sixth is rate-limited and sends nothing.
    String shared = "01910000001";
    User[] us = new User[6];
    for (int i = 0; i < 6; i++) us[i] = student("shared" + i);
    for (int i = 0; i < 5; i++){
      profiles.requestPhoneChange(us[i].getId(), shared, "127.0.0.1");
    }
    String otpAfterFive = TestProviders.SMS_OTPS.get(shared);
    assertNotNull(otpAfterFive, "5 OTPs must have been sent to the shared number");
    assertThrows(Exceptions.TooManyRequests.class,
      () -> profiles.requestPhoneChange(us[5].getId(), shared, "127.0.0.1"));
    assertEquals(otpAfterFive, TestProviders.SMS_OTPS.get(shared),
      "the rate-limited 6th request must not have sent another OTP");
  }

  @Test void samePhoneRejected(){
    User u = student("same");
    u.setPhone("01712345678"); users.save(u);
    var ex = assertThrows(Exceptions.BadRequest.class,
      () -> profiles.requestPhoneChange(u.getId(), "01712345678", "127.0.0.1"));
    assertEquals("PHONE_UNCHANGED", ex.code());
  }

  @Test void invalidPhoneRejected(){
    User u = student("invalid");
    for (String bad : new String[]{"123", "+8801712345678", "0171234567", "02712345678", "017123456789", ""}){
      var ex = assertThrows(Exceptions.BadRequest.class,
        () -> profiles.requestPhoneChange(u.getId(), bad, "127.0.0.1"));
      assertEquals("INVALID_PHONE", ex.code(), "must reject: " + bad);
    }
    assertThrows(Exceptions.BadRequest.class, () -> profiles.requestPhoneChange(u.getId(), null, "127.0.0.1"));
  }

  @Test void suspendedNumberRejected(){
    User suspended = student("susp2");
    suspended.setPhone("01799999999"); suspended.setState("SUSPENDED"); users.save(suspended);
    User u = student("victim2");
    var ex = assertThrows(Exceptions.Forbidden.class,
      () -> profiles.requestPhoneChange(u.getId(), "01799999999", "127.0.0.1"));
    assertEquals("FORBIDDEN", ex.code());
    assertNull(fresh(u).getPendingPhone());
  }

  @Test void cancelClearsPendingAndVerifyAfterCancelFails(){
    User u = student("cancel");
    requestOtp(u, "01712345678");
    profiles.cancelPhoneChange(u.getId());
    assertNull(fresh(u).getPendingPhone());
    assertNull(fresh(u).getPendingPhoneRequestedAt());
    var ex = assertThrows(Exceptions.BadRequest.class, () -> profiles.verifyPhoneChange(u.getId(), "123456"));
    assertEquals("NO_PENDING_PHONE_CHANGE", ex.code());
    assertEquals(u.getPhone(), fresh(u).getPhone());
  }

  @Test void verifyWithoutPendingFails(){
    User u = student("nopending");
    var ex = assertThrows(Exceptions.BadRequest.class, () -> profiles.verifyPhoneChange(u.getId(), "123456"));
    assertEquals("NO_PENDING_PHONE_CHANGE", ex.code());
  }

  @Test void gatewayConfiguredFlag(){
    assertTrue(otp.smsGatewayConfigured(), "this class runs with app.sms.provider=gateway");
  }
}
