package com.unilink.admin;
import com.unilink.audit.AuditService; import com.unilink.common.Exceptions; import com.unilink.common.Hashing;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.integration.email.EmailProvider; import com.unilink.security.*;
import com.unilink.identity.service.AuthService; import com.unilink.identity.service.SessionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*; import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom; import java.time.LocalDateTime; import java.util.*;
/** Admin auth (D-031/D-068): TOTP enrolment at first login, 10 hashed single-use recovery codes,
 *  email-OTP fallback that NEVER satisfies 2FA for a Platform Admin. */
@Service
public class AdminAuthService {
  private final AdminAccountRepository admins; private final AdminTotpSecretRepository totpSecrets;
  private final AdminRecoveryCodeRepository recoveryCodes; private final AdminEmailOtpRepository emailOtps;
  private final AuthenticationManager authManager; private final TotpService totp; private final PasswordEncoder encoder;
  private final LoginThrottle throttle; private final AuditService audit; private final EmailProvider email;
  private final SessionService sessions; private final AdminIpAllowlistRepository ipAllowlist;
  private final String encryptionKey; private final String totpIssuer;
  /** D-096: recovery codes expire 90 days after enrolment. */
  public static final java.time.Duration RECOVERY_CODE_VALIDITY = java.time.Duration.ofDays(90);
  public AdminAuthService(AdminAccountRepository admins, AdminTotpSecretRepository totpSecrets,
      AdminRecoveryCodeRepository recoveryCodes, AdminEmailOtpRepository emailOtps,
      @Qualifier("adminAuthManager") AuthenticationManager authManager, TotpService totp, PasswordEncoder encoder,
      LoginThrottle throttle, AuditService audit, EmailProvider email, SessionService sessions,
      AdminIpAllowlistRepository ipAllowlist,
      @Value("${app.encryption-key}") String encryptionKey,
      @Value("${TOTP_ISSUER:UniLink}") String totpIssuer){
    this.admins=admins; this.totpSecrets=totpSecrets; this.recoveryCodes=recoveryCodes; this.emailOtps=emailOtps;
    this.authManager=authManager; this.totp=totp; this.encoder=encoder; this.throttle=throttle;
    this.audit=audit; this.email=email; this.sessions=sessions; this.ipAllowlist=ipAllowlist; this.encryptionKey=encryptionKey; this.totpIssuer=totpIssuer;
  }
  public record LoginResult(Principals.AdminPrincipal principal, String stage, String otpauthUrl, boolean mustChangePassword) {}
  /** D-094: whether the stored admin account carries the must-change flag. */
  public boolean mustChangePassword(Long adminId){
    return admins.findById(adminId).map(a -> Boolean.TRUE.equals(a.getMustChangePassword())).orElse(false);
  }
  /** I-16: true when the allow-list is empty (opt-in, not configured) or the
   *  client IP matches an active row. Rows are exact IPv4/IPv6 literals or
   *  IPv4 CIDR ranges (e.g. 203.0.113.0/24). */
  boolean isIpAllowed(String ip){
    List<AdminIpAllowlist> rows = ipAllowlist.findByActiveTrue();
    if (rows.isEmpty()) return true;
    if (ip==null || ip.isBlank()) return false;
    return rows.stream().anyMatch(r -> r.getCidr()!=null && ipMatchesCidr(ip.trim(), r.getCidr().trim()));
  }
  /** Package-private for unit tests: exact match, or IPv4 CIDR containment. */
  static boolean ipMatchesCidr(String ip, String cidr){
    if (cidr.contains("/")){
      String[] parts = cidr.split("/", 2);
      int prefix;
      try { prefix = Integer.parseInt(parts[1]); } catch (NumberFormatException e){ return false; }
      if (prefix<0 || prefix>32) return false;
      long ipLong = parseIpv4(ip); long netLong = parseIpv4(parts[0]);
      if (ipLong<0 || netLong<0) return false;
      long mask = prefix==0 ? 0L : (0xFFFFFFFFL << (32-prefix)) & 0xFFFFFFFFL;
      return (ipLong & mask) == (netLong & mask);
    }
    return ip.equals(cidr);
  }
  private static long parseIpv4(String ip){
    String[] octets = ip.split("\\.", -1);
    if (octets.length!=4) return -1;
    long v = 0;
    for (String o : octets){
      int n;
      try { n = Integer.parseInt(o); } catch (NumberFormatException e){ return -1; }
      if (n<0 || n>255) return -1;
      v = (v<<8) | n;
    }
    return v;
  }
  public LoginResult login(String email, String password, String ip){
    if (throttle.isLocked("admin-login:"+email)) throw new Exceptions.TooManyRequests("Too many failed admin logins. Try again in 15 minutes.");
    Authentication auth;
    try { auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password)); }
    catch (org.springframework.security.core.AuthenticationException e){
      throttle.recordFailure("admin-login:"+email);
      audit.write(null, "ADMIN_LOGIN_FAILED", "ADMIN", null, "Failed admin login for "+email, Map.of(), ip);
      throw new Exceptions.BadRequest("INVALID_CREDENTIALS","Email or password is incorrect");
    }
    var principal = (Principals.AdminPrincipal) auth.getPrincipal();
    AdminAccount account = admins.findById(principal.adminId()).orElse(null);
    // D-095: a temporary password is only good for 24 h. The password was
    // correct (authentication already succeeded), so this is a BadRequest
    // with a clear message, not a generic invalid-credentials failure.
    // A Platform Admin must re-issue the credentials.
    if (account!=null && account.getTempPasswordExpiresAt()!=null
        && account.getTempPasswordExpiresAt().isBefore(LocalDateTime.now()))
      throw new Exceptions.BadRequest("TEMP_PASSWORD_EXPIRED","Your temporary password has expired. Ask a Platform Admin to issue new credentials.");
    // I-16: the admin IP allow-list (NFR-SEC-07, D-031) is enforced here, after
    // the password is verified and before any TOTP stage is reached. It is
    // opt-in: with no active allow-list rows every IP is allowed (previous
    // behaviour); once a Platform Admin configures rows, only matching IPs
    // may log in. A blocked login is audited and does not reset the throttle.
    if (!isIpAllowed(ip)){
      audit.write(principal.adminId(), "ADMIN_LOGIN_IP_BLOCKED", "ADMIN", principal.adminId(), "Login blocked by IP allow-list for "+ip, Map.of(), ip);
      throw new Exceptions.Forbidden("This IP address is not allowed to sign in to the Admin Portal");
    }
    throttle.recordSuccess("admin-login:"+email);
    audit.write(principal.adminId(), "ADMIN_LOGIN_PASSWORD_OK", "ADMIN", principal.adminId(), null, Map.of(), ip);
    boolean mustChange = Boolean.TRUE.equals(account==null?null:account.getMustChangePassword());
    if (!Boolean.TRUE.equals(account==null?null:account.getTotpEnrolled())){
      String secret = totp.generateSecret();
      AdminTotpSecret row = totpSecrets.findById(principal.adminId()).orElseGet(AdminTotpSecret::new);
      row.setId(principal.adminId()); row.setSecretEncrypted(AesGcm.encrypt(encryptionKey, secret));
      totpSecrets.save(row);
      return new LoginResult(principal, "NEEDS_ENROLMENT", totp.otpauthUrl(secret, email, totpIssuer), mustChange);
    }
    return new LoginResult(principal, "NEEDS_TOTP", null, mustChange);
  }
  @Transactional
  public List<String> enrolTotp(Long adminId, String code, String ip){
    String key = "admin-totp:"+adminId;
    if (throttle.isLocked(key)) throw new Exceptions.TooManyRequests("Too many failed authenticator attempts. Try again in 15 minutes.");
    if (code==null || code.isBlank()) throw new Exceptions.BadRequest("CODE_REQUIRED","An authenticator code is required");
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    if (Boolean.TRUE.equals(a.getTotpEnrolled()))
      throw new Exceptions.BadRequest("ALREADY_ENROLLED","Authenticator already enrolled. If you lost your authenticator, ask a Platform Admin to reset your credentials.");
    AdminTotpSecret row = totpSecrets.findById(adminId).orElseThrow(() -> new Exceptions.BadRequest("NO_TOTP_SECRET","No TOTP enrolment in progress"));
    String secret = AesGcm.decrypt(encryptionKey, row.getSecretEncrypted());
    if (!totp.verify(secret, code)){
      throttle.recordFailure(key);
      throw new Exceptions.BadRequest("INVALID_TOTP","Incorrect authenticator code");
    }
    throttle.recordSuccess(key);
    a.setTotpEnrolled(true); admins.save(a);
    recoveryCodes.deleteByAdminId(adminId);
    List<String> plain = new ArrayList<>();
    SecureRandom rnd = new SecureRandom();
    for (int i=0;i<10;i++){
      String c = String.format("%04d-%04d", rnd.nextInt(10000), rnd.nextInt(10000));
      plain.add(c);
      AdminRecoveryCode rc = new AdminRecoveryCode(); rc.setAdminId(adminId);
      // D-096: salted Argon2id (the existing PasswordEncoder), not unsalted
      // SHA-256. Codes expire 90 days after enrolment; re-enrolment deletes
      // all prior codes, so it also retires them.
      rc.setCodeHash(encoder.encode(c)); rc.setUsed(false);
      rc.setExpiresAt(LocalDateTime.now().plus(RECOVERY_CODE_VALIDITY));
      recoveryCodes.save(rc);
    }
    audit.write(adminId, "ADMIN_TOTP_ENROLLED", "ADMIN", adminId, null, Map.of(), ip);
    return plain;
  }
  /** D-093: admin password change. MFA-verified only — the controller
   *  enforces that (AccessPolicy.requireAdmin equivalent); this service
   *  method never runs for a merely password-authenticated principal.
   *  Admin rule (D-031): at least 12 characters. The new password must
   *  differ from the current one. Success clears the must-change flag and
   *  any temporary-password expiry, invalidates every session for the
   *  account (D-013) and is audited. */
  @Transactional
  public void changePassword(Long adminId, String oldPassword, String newPassword, String ip){
    if (newPassword==null || newPassword.length()<12)
      throw new Exceptions.BadRequest("WEAK_PASSWORD","Admin passwords must be at least 12 characters (D-031)");
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    if (oldPassword==null || !encoder.matches(oldPassword, a.getPasswordHash()))
      throw new Exceptions.BadRequest("INVALID_CREDENTIALS","Current password is incorrect");
    if (encoder.matches(newPassword, a.getPasswordHash()))
      throw new Exceptions.BadRequest("SAME_PASSWORD","New password must be different from the current password");
    a.setPasswordHash(encoder.encode(newPassword));
    a.setMustChangePassword(false); a.setTempPasswordExpiresAt(null); admins.save(a);
    sessions.invalidateAllSessions(a.getEmail());
    audit.write(adminId, "ADMIN_PASSWORD_CHANGED", "ADMIN", adminId, null, Map.of(), ip);
  }
  @Transactional
  public Principals.AdminPrincipal verifyTotpOrRecovery(Long adminId, String code, String ip){
    String key = "admin-totp:"+adminId;
    if (throttle.isLocked(key)) throw new Exceptions.TooManyRequests("Too many failed authenticator attempts. Try again in 15 minutes.");
    if (code==null || code.isBlank()) throw new Exceptions.BadRequest("CODE_REQUIRED","An authenticator or recovery code is required");
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    AdminTotpSecret row = totpSecrets.findById(adminId).orElseThrow(() -> new Exceptions.BadRequest("NO_TOTP_SECRET","TOTP is not set up"));
    String secret = AesGcm.decrypt(encryptionKey, row.getSecretEncrypted());
    if (totp.verify(secret, code)){
      throttle.recordSuccess(key);
      audit.write(adminId, "ADMIN_LOGIN_OK", "ADMIN", adminId, null, Map.of("mfa","totp"), ip);
      return new Principals.AdminPrincipal(a.getId(), a.getEmail(), a.getPasswordHash(), a.getRole(), a.getUniversityId(), true, Boolean.TRUE.equals(a.getActive()));
    }
    // Recovery code (single-use, Argon2id-hashed, D-096). A code only counts
    // while unused and unexpired; re-enrolment deletes all prior codes.
    // Legacy rows still carry an unsalted SHA-256 hex digest (64 chars, no
    // "$argon2" prefix): they can never verify under Argon2id and are
    // skipped, i.e. pre-V010 codes are invalidated — the admin re-enrols
    // TOTP (which issues fresh codes) or a Platform Admin resets the
    // credentials. The prefix guard also keeps encoder.matches from
    // throwing on a non-Argon2 hash.
    for (AdminRecoveryCode rc : recoveryCodes.findByAdminIdAndUsedFalse(adminId)){
      if (isRecoveryCodeExpired(rc)) continue;
      if (rc.getCodeHash()==null || !rc.getCodeHash().startsWith("$argon2")) continue;
      if (encoder.matches(code, rc.getCodeHash())){
        throttle.recordSuccess(key);
        rc.setUsed(true); rc.setUsedAt(LocalDateTime.now()); recoveryCodes.save(rc);
        audit.write(adminId, "ADMIN_LOGIN_OK", "ADMIN", adminId, null, Map.of("mfa","recovery_code"), ip);
        return new Principals.AdminPrincipal(a.getId(), a.getEmail(), a.getPasswordHash(), a.getRole(), a.getUniversityId(), true, Boolean.TRUE.equals(a.getActive()));
      }
    }
    throttle.recordFailure(key);
    audit.write(adminId, "ADMIN_LOGIN_FAILED", "ADMIN", adminId, "Wrong TOTP/recovery code", Map.of("mfa","totp"), ip);
    throw new Exceptions.BadRequest("INVALID_TOTP","Incorrect authenticator or recovery code");
  }
  /** D-096: a recovery code is expired when its expires_at has passed, or —
   *  for pre-V010 rows with no expires_at — when createdAt is more than 90
   *  days old. A row with neither timestamp is treated as expired: it
   *  predates the expiry policy and must not be trusted indefinitely. */
  public static boolean isRecoveryCodeExpired(AdminRecoveryCode rc){
    if (rc.getExpiresAt()!=null) return rc.getExpiresAt().isBefore(LocalDateTime.now());
    if (rc.getCreatedAt()!=null) return rc.getCreatedAt().plus(RECOVERY_CODE_VALIDITY).isBefore(LocalDateTime.now());
    return true;
  }
  /** Email-OTP requests are rate-limited (5 per hour per admin; the attempt
   *  that exceeds the cap locks the key for 15 minutes) so the lock can
   *  actually engage. A fresh request while a live OTP exists carries the
   *  previous row's attempt count forward instead of resetting it to 0, so
   *  requesting a new code cannot wipe the 5-attempt verify limit. */
  @Transactional
  public void requestEmailOtp(Long adminId){
    String key = "admin-email-otp:"+adminId;
    if (throttle.isLocked(key)) throw new Exceptions.TooManyRequests("Too many email OTP requests. Try again later.");
    if (!throttle.tryAcquire(key, 5, java.time.Duration.ofHours(1), java.time.Duration.ofMinutes(15)))
      throw new Exceptions.TooManyRequests("Too many email OTP requests. Try again later.");
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    var live = emailOtps.findTopByAdminIdAndUsedFalseOrderByCreatedAtDesc(adminId)
      .filter(r -> r.getExpiresAt()!=null && r.getExpiresAt().isAfter(LocalDateTime.now()));
    int attempts = live.map(r -> r.getAttempts()==null?0:r.getAttempts()).orElse(0);
    // The fresh OTP supersedes the previous live one, so only one OTP is
    // ever active (verify only ever checks the newest unused row).
    live.ifPresent(r -> { r.setUsed(true); emailOtps.save(r); });
    String otp = com.unilink.identity.service.OtpService.newOtp();
    AdminEmailOtp row = new AdminEmailOtp(); row.setAdminId(adminId); row.setOtpHash(Hashing.sha256(otp));
    row.setAttempts(attempts); row.setUsed(false); row.setExpiresAt(LocalDateTime.now().plusMinutes(10));
    emailOtps.save(row);
    email.sendOtp(a.getEmail(), otp);
    audit.write(adminId, "ADMIN_EMAIL_OTP_SENT", "ADMIN", adminId, null, Map.of(), null);
  }
  @Transactional
  public Principals.AdminPrincipal verifyEmailOtp(Long adminId, String otp, String ip){
    if (otp==null || otp.isBlank()) throw new Exceptions.BadRequest("CODE_REQUIRED","An email OTP is required");
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    if ("PLATFORM_ADMIN".equals(a.getRole()))
      throw new Exceptions.Forbidden("Email OTP alone never satisfies 2FA for a Platform Admin (D-068). Use your authenticator app or a recovery code.");
    AdminEmailOtp row = emailOtps.findTopByAdminIdAndUsedFalseOrderByCreatedAtDesc(adminId)
      .orElseThrow(() -> new Exceptions.BadRequest("OTP_NOT_FOUND","No active email OTP."));
    if (row.getExpiresAt().isBefore(LocalDateTime.now())) throw new Exceptions.BadRequest("OTP_EXPIRED","Email OTP has expired.");
    if (row.getAttempts()>=5) throw new Exceptions.BadRequest("OTP_ATTEMPTS_EXCEEDED","Too many wrong attempts.");
    if (!Hashing.sha256(otp).equals(row.getOtpHash())){
      // D-117: atomic REQUIRES_NEW increment (same D-103 bug class as the
      // phone/password-reset OTPs) — a setAttempts+save here would roll back
      // with the OTP_INVALID exception and the 5-attempt cap could never engage.
      emailOtps.incrementAttempts(row.getId());
      throw new Exceptions.BadRequest("OTP_INVALID","Incorrect code");
    }
    row.setUsed(true); emailOtps.save(row);
    audit.write(adminId, "ADMIN_LOGIN_OK", "ADMIN", adminId, null, Map.of("mfa","email_otp"), ip);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), a.getPasswordHash(), a.getRole(), a.getUniversityId(), true, Boolean.TRUE.equals(a.getActive()));
  }
}
