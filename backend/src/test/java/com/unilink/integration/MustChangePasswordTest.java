package com.unilink.integration;

import com.unilink.admin.AdminAuthService;
import com.unilink.admin.AdminService;
import com.unilink.common.Exceptions;
import com.unilink.common.Hashing;
import com.unilink.identity.domain.*;
import com.unilink.identity.dto.AuthDtos.ChangePasswordRequest;
import com.unilink.identity.repo.*;
import com.unilink.identity.service.AuthService;
import com.unilink.security.AesGcm;
import com.unilink.security.LoginThrottle;
import com.unilink.security.Principals;
import com.unilink.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 3 Worker A (D-093..D-096): admin password change, must-change
 *  enforcement on both reset paths, 24 h temporary-password expiry, and
 *  Argon2id recovery codes with 90-day expiry. Service-level tests on H2. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-mustchange;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-mustchange-key",
  // D-091: these tests exercise paths that invalidate sessions
  // (FORCE_RESET, credential reset, password change) via SessionService,
  // which queries SPRING_SESSION. H2 test databases have no such tables
  // unless Spring Session creates them.
  "spring.session.jdbc.initialize-schema=always"
})
@Transactional
class MustChangePasswordTest {
  private static final String ENCRYPTION_KEY = "test-mustchange-key";
  @Autowired TestDataFactory factory;
  @Autowired AdminAuthService adminAuth;
  @Autowired AdminService adminService;
  @Autowired AuthService authService;
  @Autowired AdminAccountRepository admins;
  @Autowired AdminTotpSecretRepository totpSecrets;
  @Autowired AdminRecoveryCodeRepository recoveryCodes;
  @Autowired UserCredentialRepository creds;
  @Autowired TotpService totp;
  @Autowired PasswordEncoder encoder;
  @Autowired LoginThrottle throttle;

  @BeforeEach void setup() { throttle.resetForTests(); TestProviders.reset(); }

  private Principals.AdminPrincipal platformAdmin() {
    return new Principals.AdminPrincipal(999L, "platform@example.com", "x", "PLATFORM_ADMIN", null, true, true);
  }
  private String email(String prefix) { return prefix + "-" + System.nanoTime() + "@example.com"; }
  private AdminAccount adminWithSecret(String email, boolean enrolled) {
    AdminAccount a = factory.admin(email, "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
    String secret = totp.generateSecret();
    AdminTotpSecret row = new AdminTotpSecret();
    row.setId(a.getId());
    row.setSecretEncrypted(AesGcm.encrypt(ENCRYPTION_KEY, secret));
    totpSecrets.save(row);
    a.setTotpEnrolled(enrolled);
    return admins.save(a);
  }
  private String secretFor(Long adminId) {
    return AesGcm.decrypt(ENCRYPTION_KEY, totpSecrets.findById(adminId).orElseThrow().getSecretEncrypted());
  }
  private String validCode(String secret) throws Exception {
    Method gen = TotpService.class.getDeclaredMethod("generate", String.class, long.class);
    gen.setAccessible(true);
    long step = java.time.Instant.now().getEpochSecond() / 30;
    return (String) gen.invoke(totp, secret, step);
  }
  private static <T extends Throwable> T assertCode(Class<T> type, String code, Runnable r) {
    T e = assertThrows(type, r::run);
    assertEquals(code, ((Exceptions.AppException) e).code());
    return e;
  }

  // ---- D-093: admin password change ----
  @Test void adminChangePasswordRejectsWrongCurrentAndWeakAndSame() {
    AdminAccount a = factory.admin(email("chg"), "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
    assertCode(Exceptions.BadRequest.class, "INVALID_CREDENTIALS",
      () -> adminAuth.changePassword(a.getId(), "WrongPassword123", "NewAdminPass123", "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "WEAK_PASSWORD",
      () -> adminAuth.changePassword(a.getId(), "Admin12345678", "short", "127.0.0.1"));
    assertCode(Exceptions.BadRequest.class, "SAME_PASSWORD",
      () -> adminAuth.changePassword(a.getId(), "Admin12345678", "Admin12345678", "127.0.0.1"));
  }

  @Test void adminChangePasswordSucceedsClearsFlagAndOldPasswordStopsWorking() {
    AdminAccount a = factory.admin(email("chg-ok"), "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
    a.setMustChangePassword(true);
    a.setTempPasswordExpiresAt(LocalDateTime.now().plusHours(1));
    admins.save(a);
    adminAuth.changePassword(a.getId(), "Admin12345678", "NewAdminPass123", "127.0.0.1");
    AdminAccount after = admins.findById(a.getId()).orElseThrow();
    assertFalse(Boolean.TRUE.equals(after.getMustChangePassword()));
    assertNull(after.getTempPasswordExpiresAt());
    assertTrue(encoder.matches("NewAdminPass123", after.getPasswordHash()));
    // Old password no longer authenticates; new one does.
    assertCode(Exceptions.BadRequest.class, "INVALID_CREDENTIALS",
      () -> adminAuth.login(a.getEmail(), "Admin12345678", "127.0.0.1"));
    var ok = adminAuth.login(a.getEmail(), "NewAdminPass123", "127.0.0.1");
    assertFalse(ok.mustChangePassword());
  }

  // ---- D-094/D-095: moderator credential reset ----
  @Test void moderatorResetSetsFlagAndExpiryAndLoginSignalsMustChange() {
    AdminAccount mod = factory.admin(email("mod-reset"), "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
    var result = adminService.resetModeratorCredentials(platformAdmin(), mod.getId(), "127.0.0.1");
    assertNotNull(result.temporaryPassword());
    AdminAccount after = admins.findById(mod.getId()).orElseThrow();
    assertTrue(Boolean.TRUE.equals(after.getMustChangePassword()));
    assertNotNull(after.getTempPasswordExpiresAt());
    assertTrue(after.getTempPasswordExpiresAt().isAfter(LocalDateTime.now().plusHours(23)));
    assertTrue(after.getTempPasswordExpiresAt().isBefore(LocalDateTime.now().plusHours(25)));
    var login = adminAuth.login(mod.getEmail(), result.temporaryPassword(), "127.0.0.1");
    assertTrue(login.mustChangePassword());
    assertEquals("NEEDS_ENROLMENT", login.stage()); // TOTP was reset too
  }

  @Test void expiredModeratorTempPasswordRejectsLogin() {
    AdminAccount mod = factory.admin(email("mod-exp"), "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
    var result = adminService.resetModeratorCredentials(platformAdmin(), mod.getId(), "127.0.0.1");
    AdminAccount after = admins.findById(mod.getId()).orElseThrow();
    after.setTempPasswordExpiresAt(LocalDateTime.now().minusMinutes(1));
    admins.save(after);
    var e = assertCode(Exceptions.BadRequest.class, "TEMP_PASSWORD_EXPIRED",
      () -> adminAuth.login(mod.getEmail(), result.temporaryPassword(), "127.0.0.1"));
    assertTrue(e.getMessage().contains("expired"));
  }

  @Test void plainAdminPasswordWithoutExpiryLogsInNormally() {
    AdminAccount a = factory.admin(email("plain"), "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
    var login = adminAuth.login(a.getEmail(), "Admin12345678", "127.0.0.1");
    assertFalse(login.mustChangePassword());
  }

  // ---- D-094/D-095: FORCE_RESET on a student ----
  @Test void forceResetSetsFlagAndExpiryAndStudentLoginSignalsMustChange() {
    var fx = factory.seedMaster();
    User u = factory.verifiedStudent(email("stu-reset"), "SR1", fx.mu(), fx.muCse(), fx.muBatch());
    var result = adminService.changeUserState(platformAdmin(), u.getId(), "FORCE_RESET", "test reset", "127.0.0.1");
    assertNotNull(result.temporaryPassword());
    UserCredential c = creds.findById(u.getId()).orElseThrow();
    assertTrue(Boolean.TRUE.equals(c.getMustChangePassword()));
    assertNotNull(c.getTempPasswordExpiresAt());
    assertTrue(authService.mustChangePassword(u.getId()));
    assertNotNull(authService.login(u.getEmail(), result.temporaryPassword(), "127.0.0.1"));
  }

  @Test void expiredStudentTempPasswordRejectsLogin() {
    var fx = factory.seedMaster();
    User u = factory.verifiedStudent(email("stu-exp"), "SE1", fx.mu(), fx.muCse(), fx.muBatch());
    var result = adminService.changeUserState(platformAdmin(), u.getId(), "FORCE_RESET", "test reset", "127.0.0.1");
    UserCredential c = creds.findById(u.getId()).orElseThrow();
    c.setTempPasswordExpiresAt(LocalDateTime.now().minusMinutes(1));
    creds.save(c);
    assertCode(Exceptions.BadRequest.class, "TEMP_PASSWORD_EXPIRED",
      () -> authService.login(u.getEmail(), result.temporaryPassword(), "127.0.0.1"));
  }

  @Test void studentChangePasswordClearsFlagAndExpiry() {
    var fx = factory.seedMaster();
    User u = factory.verifiedStudent(email("stu-chg"), "SC1", fx.mu(), fx.muCse(), fx.muBatch());
    UserCredential c = creds.findById(u.getId()).orElseThrow();
    c.setMustChangePassword(true);
    c.setTempPasswordExpiresAt(LocalDateTime.now().plusHours(1));
    creds.save(c);
    authService.changePassword(u.getId(), new ChangePasswordRequest("Student123", "NewStudent1"));
    UserCredential after = creds.findById(u.getId()).orElseThrow();
    assertFalse(Boolean.TRUE.equals(after.getMustChangePassword()));
    assertNull(after.getTempPasswordExpiresAt());
    assertNotNull(authService.login(u.getEmail(), "NewStudent1", "127.0.0.1"));
    assertFalse(authService.mustChangePassword(u.getId()));
  }

  // ---- D-096: recovery codes ----
  @Test void enrolledRecoveryCodesAreArgon2HashedWithExpiryAndSingleUse() throws Exception {
    AdminAccount a = adminWithSecret(email("rec"), false);
    List<String> codes = adminAuth.enrolTotp(a.getId(), validCode(secretFor(a.getId())), "127.0.0.1");
    assertEquals(10, codes.size());
    List<AdminRecoveryCode> rows = recoveryCodes.findByAdminIdAndUsedFalse(a.getId());
    assertEquals(10, rows.size());
    for (AdminRecoveryCode rc : rows) {
      assertTrue(rc.getCodeHash().startsWith("$argon2"), "code hash must be Argon2id, was: " + rc.getCodeHash());
      assertNotNull(rc.getExpiresAt());
      assertTrue(rc.getExpiresAt().isAfter(LocalDateTime.now().plusDays(89)));
    }
    String first = codes.get(0);
    assertNotNull(adminAuth.verifyTotpOrRecovery(a.getId(), first, "127.0.0.1"));
    // Single use: the same code must not verify twice.
    assertCode(Exceptions.BadRequest.class, "INVALID_TOTP",
      () -> adminAuth.verifyTotpOrRecovery(a.getId(), first, "127.0.0.1"));
    assertEquals(9, recoveryCodes.findByAdminIdAndUsedFalse(a.getId()).size());
  }

  @Test void legacySha256RecoveryCodeIsInvalidated() {
    AdminAccount a = adminWithSecret(email("rec-legacy"), true);
    AdminRecoveryCode rc = new AdminRecoveryCode();
    rc.setAdminId(a.getId());
    rc.setCodeHash(Hashing.sha256("1234-5678")); // pre-V010 unsalted SHA-256 row
    rc.setUsed(false);
    recoveryCodes.save(rc);
    assertCode(Exceptions.BadRequest.class, "INVALID_TOTP",
      () -> adminAuth.verifyTotpOrRecovery(a.getId(), "1234-5678", "127.0.0.1"));
  }

  @Test void expiredArgon2RecoveryCodeIsRejectedAndLiveOneAccepted() {
    AdminAccount a = adminWithSecret(email("rec-exp"), true);
    AdminRecoveryCode expired = new AdminRecoveryCode();
    expired.setAdminId(a.getId());
    expired.setCodeHash(encoder.encode("1111-2222"));
    expired.setUsed(false);
    expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    recoveryCodes.save(expired);
    AdminRecoveryCode live = new AdminRecoveryCode();
    live.setAdminId(a.getId());
    live.setCodeHash(encoder.encode("3333-4444"));
    live.setUsed(false);
    live.setExpiresAt(LocalDateTime.now().plusDays(90));
    recoveryCodes.save(live);
    assertCode(Exceptions.BadRequest.class, "INVALID_TOTP",
      () -> adminAuth.verifyTotpOrRecovery(a.getId(), "1111-2222", "127.0.0.1"));
    assertNotNull(adminAuth.verifyTotpOrRecovery(a.getId(), "3333-4444", "127.0.0.1"));
  }

  @Test void recoveryCodeExpiryFallsBackToCreatedAtPlus90Days() {
    AdminRecoveryCode old = new AdminRecoveryCode();
    old.setCreatedAt(LocalDateTime.now().minusDays(91));
    assertTrue(AdminAuthService.isRecoveryCodeExpired(old));
    AdminRecoveryCode fresh = new AdminRecoveryCode();
    fresh.setCreatedAt(LocalDateTime.now().minusDays(10));
    assertFalse(AdminAuthService.isRecoveryCodeExpired(fresh));
    AdminRecoveryCode dateless = new AdminRecoveryCode();
    assertTrue(AdminAuthService.isRecoveryCodeExpired(dateless));
  }
}
