package com.unilink.admin;
import com.unilink.audit.AuditService; import com.unilink.common.Exceptions; import com.unilink.common.Hashing;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.integration.email.EmailProvider; import com.unilink.security.*;
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
  private final String encryptionKey; private final String totpIssuer;
  public AdminAuthService(AdminAccountRepository admins, AdminTotpSecretRepository totpSecrets,
      AdminRecoveryCodeRepository recoveryCodes, AdminEmailOtpRepository emailOtps,
      @Qualifier("adminAuthManager") AuthenticationManager authManager, TotpService totp, PasswordEncoder encoder,
      LoginThrottle throttle, AuditService audit, EmailProvider email,
      @Value("${app.encryption-key}") String encryptionKey,
      @Value("${TOTP_ISSUER:UniLink}") String totpIssuer){
    this.admins=admins; this.totpSecrets=totpSecrets; this.recoveryCodes=recoveryCodes; this.emailOtps=emailOtps;
    this.authManager=authManager; this.totp=totp; this.encoder=encoder; this.throttle=throttle;
    this.audit=audit; this.email=email; this.encryptionKey=encryptionKey; this.totpIssuer=totpIssuer;
  }
  public record LoginResult(Principals.AdminPrincipal principal, String stage, String otpauthUrl) {}
  public LoginResult login(String email, String password, String ip){
    if (throttle.isLocked("admin-login:"+email)) throw new Exceptions.TooManyRequests("Too many failed admin logins. Try again in 15 minutes.");
    Authentication auth;
    try { auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password)); }
    catch (org.springframework.security.core.AuthenticationException e){
      throttle.recordFailure("admin-login:"+email);
      audit.write(null, "ADMIN_LOGIN_FAILED", "ADMIN", null, "Failed admin login for "+email, Map.of(), ip);
      throw new Exceptions.BadRequest("INVALID_CREDENTIALS","Email or password is incorrect");
    }
    throttle.recordSuccess("admin-login:"+email);
    var principal = (Principals.AdminPrincipal) auth.getPrincipal();
    audit.write(principal.adminId(), "ADMIN_LOGIN_PASSWORD_OK", "ADMIN", principal.adminId(), null, Map.of(), ip);
    if (!Boolean.TRUE.equals(admins.findById(principal.adminId()).map(AdminAccount::getTotpEnrolled).orElse(false))){
      String secret = totp.generateSecret();
      AdminTotpSecret row = totpSecrets.findByAdminId(principal.adminId()).orElseGet(AdminTotpSecret::new);
      row.setId(principal.adminId()); row.setSecretEncrypted(AesGcm.encrypt(encryptionKey, secret));
      totpSecrets.save(row);
      return new LoginResult(principal, "NEEDS_ENROLMENT", totp.otpauthUrl(secret, email, totpIssuer));
    }
    return new LoginResult(principal, "NEEDS_TOTP", null);
  }
  @Transactional
  public List<String> enrolTotp(Long adminId, String code, String ip){
    AdminTotpSecret row = totpSecrets.findByAdminId(adminId).orElseThrow(() -> new Exceptions.BadRequest("NO_TOTP_SECRET","No TOTP enrolment in progress"));
    String secret = AesGcm.decrypt(encryptionKey, row.getSecretEncrypted());
    if (!totp.verify(secret, code)) throw new Exceptions.BadRequest("INVALID_TOTP","Incorrect authenticator code");
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    a.setTotpEnrolled(true); admins.save(a);
    recoveryCodes.deleteByAdminId(adminId);
    List<String> plain = new ArrayList<>();
    SecureRandom rnd = new SecureRandom();
    for (int i=0;i<10;i++){
      String c = String.format("%04d-%04d", rnd.nextInt(10000), rnd.nextInt(10000));
      plain.add(c);
      AdminRecoveryCode rc = new AdminRecoveryCode(); rc.setAdminId(adminId);
      rc.setCodeHash(Hashing.sha256(c)); rc.setUsed(false); recoveryCodes.save(rc);
    }
    audit.write(adminId, "ADMIN_TOTP_ENROLLED", "ADMIN", adminId, null, Map.of(), ip);
    return plain;
  }
  @Transactional
  public Principals.AdminPrincipal verifyTotpOrRecovery(Long adminId, String code, String ip){
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    AdminTotpSecret row = totpSecrets.findByAdminId(adminId).orElseThrow(() -> new Exceptions.BadRequest("NO_TOTP_SECRET","TOTP is not set up"));
    String secret = AesGcm.decrypt(encryptionKey, row.getSecretEncrypted());
    if (totp.verify(secret, code)){
      audit.write(adminId, "ADMIN_LOGIN_OK", "ADMIN", adminId, null, Map.of("mfa","totp"), ip);
      return new Principals.AdminPrincipal(a.getId(), a.getEmail(), a.getPasswordHash(), a.getRole(), a.getUniversityId(), true, Boolean.TRUE.equals(a.getActive()));
    }
    // Recovery code (single-use, hashed)
    for (AdminRecoveryCode rc : recoveryCodes.findByAdminIdAndUsedFalse(adminId)){
      if (Hashing.sha256(code).equals(rc.getCodeHash())){
        rc.setUsed(true); rc.setUsedAt(LocalDateTime.now()); recoveryCodes.save(rc);
        audit.write(adminId, "ADMIN_LOGIN_OK", "ADMIN", adminId, null, Map.of("mfa","recovery_code"), ip);
        return new Principals.AdminPrincipal(a.getId(), a.getEmail(), a.getPasswordHash(), a.getRole(), a.getUniversityId(), true, Boolean.TRUE.equals(a.getActive()));
      }
    }
    audit.write(adminId, "ADMIN_LOGIN_FAILED", "ADMIN", adminId, "Wrong TOTP/recovery code", Map.of("mfa","totp"), ip);
    throw new Exceptions.BadRequest("INVALID_TOTP","Incorrect authenticator or recovery code");
  }
  @Transactional
  public void requestEmailOtp(Long adminId){
    if (throttle.isLocked("admin-email-otp:"+adminId)) throw new Exceptions.TooManyRequests("Too many email OTP requests. Try again later.");
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    String otp = com.unilink.identity.service.OtpService.newOtp();
    AdminEmailOtp row = new AdminEmailOtp(); row.setAdminId(adminId); row.setOtpHash(Hashing.sha256(otp));
    row.setAttempts(0); row.setUsed(false); row.setExpiresAt(LocalDateTime.now().plusMinutes(10));
    emailOtps.save(row);
    email.sendOtp(a.getEmail(), otp);
    audit.write(adminId, "ADMIN_EMAIL_OTP_SENT", "ADMIN", adminId, null, Map.of(), null);
  }
  @Transactional
  public Principals.AdminPrincipal verifyEmailOtp(Long adminId, String otp, String ip){
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    if ("PLATFORM_ADMIN".equals(a.getRole()))
      throw new Exceptions.Forbidden("Email OTP alone never satisfies 2FA for a Platform Admin (D-068). Use your authenticator app or a recovery code.");
    AdminEmailOtp row = emailOtps.findTopByAdminIdAndUsedFalseOrderByCreatedAtDesc(adminId)
      .orElseThrow(() -> new Exceptions.BadRequest("OTP_NOT_FOUND","No active email OTP."));
    if (row.getExpiresAt().isBefore(LocalDateTime.now())) throw new Exceptions.BadRequest("OTP_EXPIRED","Email OTP has expired.");
    if (row.getAttempts()>=5) throw new Exceptions.BadRequest("OTP_ATTEMPTS_EXCEEDED","Too many wrong attempts.");
    if (!Hashing.sha256(otp).equals(row.getOtpHash())){
      row.setAttempts(row.getAttempts()+1); emailOtps.save(row);
      throw new Exceptions.BadRequest("OTP_INVALID","Incorrect code");
    }
    row.setUsed(true); emailOtps.save(row);
    audit.write(adminId, "ADMIN_LOGIN_OK", "ADMIN", adminId, null, Map.of("mfa","email_otp"), ip);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), a.getPasswordHash(), a.getRole(), a.getUniversityId(), true, Boolean.TRUE.equals(a.getActive()));
  }
}
