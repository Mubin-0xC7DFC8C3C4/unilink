package com.unilink.identity.service;
import com.unilink.common.Exceptions; import com.unilink.common.Hashing;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.integration.email.EmailProvider; import com.unilink.integration.sms.SmsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom; import java.time.LocalDateTime; import java.util.UUID;
/** OTP (6-digit, 5 min, 5 attempts, hashed, single-use) + email link (24 h, hashed, single-use) — D-011. */
@Service
public class OtpService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OtpService.class);
  private final PhoneOtpRepository phoneOtps; private final EmailVerificationTokenRepository emailTokens;
  private final PasswordResetTokenRepository resetTokens; private final SmsProvider sms; private final EmailProvider email;
  private static final SecureRandom RANDOM = new SecureRandom();
  public OtpService(PhoneOtpRepository p, EmailVerificationTokenRepository e, PasswordResetTokenRepository r,
                    SmsProvider sms, EmailProvider email){
    phoneOtps=p; emailTokens=e; resetTokens=r; this.sms=sms; this.email=email;
  }
  public static String newOtp(){ return String.format("%06d", RANDOM.nextInt(1_000_000)); }
  public static String newToken(){ return UUID.randomUUID().toString().replace("-","") + UUID.randomUUID().toString().replace("-",""); }

  /** Sends phone OTP by SMS; on gateway failure falls back to email OTP (NFR-FLT-02 / D-011). Returns channel used. */
  @Transactional
  public String sendPhoneOtp(User user){
    String otp = newOtp();
    PhoneOtp row = new PhoneOtp();
    row.setUserId(user.getId()); row.setPhone(user.getPhone()); row.setOtpHash(Hashing.sha256(otp));
    row.setChannel("SMS"); row.setAttempts(0); row.setUsed(false);
    row.setExpiresAt(LocalDateTime.now().plusMinutes(5));
    phoneOtps.save(row);
    try { sms.sendOtp(user.getPhone(), otp); return "SMS"; }
    catch (SmsProvider.ProviderException ex){
      row.setChannel("EMAIL"); phoneOtps.save(row);
      email.sendOtp(user.getEmail(), otp); // fallback channel, same OTP record
      return "EMAIL";
    }
  }
  @Transactional
  public void verifyPhoneOtp(User user, String otp){
    PhoneOtp row = phoneOtps.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
      .orElseThrow(() -> new Exceptions.BadRequest("OTP_NOT_FOUND","No active OTP. Request a new one."));
    if (row.getExpiresAt().isBefore(LocalDateTime.now())) throw new Exceptions.BadRequest("OTP_EXPIRED","OTP has expired. Request a new one.");
    if (row.getAttempts()>=5) throw new Exceptions.BadRequest("OTP_ATTEMPTS_EXCEEDED","Too many wrong attempts. Request a new OTP.");
    if (!Hashing.sha256(otp).equals(row.getOtpHash())){
      // Atomic REQUIRES_NEW increment (D-103): the OTP_INVALID exception
      // below rolls back this method's transaction, which used to roll
      // back the attempt count as well — the cap could never engage.
      phoneOtps.incrementAttempts(row.getId());
      throw new Exceptions.BadRequest("OTP_INVALID","Incorrect code");
    }
    row.setUsed(true); phoneOtps.save(row);
    user.setPhoneVerifiedAt(LocalDateTime.now());
  }
  @Transactional
  public String sendEmailVerificationLink(User user, String baseUrl){
    String token = newToken();
    EmailVerificationToken row = new EmailVerificationToken();
    row.setUserId(user.getId()); row.setTokenHash(Hashing.sha256(token)); row.setUsed(false);
    row.setExpiresAt(LocalDateTime.now().plusHours(24));
    emailTokens.save(row);
    // A failed email send must not abort registration: the token is stored
    // and the link can be re-sent. (Production 2026-11-23: an unreachable
    // SMTP route otherwise destroyed signups entirely.)
    try { email.sendVerificationLink(user.getEmail(), baseUrl + "/api/auth/verify-email?token=" + token); }
    catch (Exception e){ log.error("Email verification link send failed for user {}: {}", user.getId(), e.getMessage()); }
    return token;
  }
  @Transactional
  public void verifyEmailToken(User user, String token){
    EmailVerificationToken row = emailTokens.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
      .orElseThrow(() -> new Exceptions.BadRequest("TOKEN_NOT_FOUND","No active verification link. Request a new one."));
    if (row.getExpiresAt().isBefore(LocalDateTime.now())) throw new Exceptions.BadRequest("TOKEN_EXPIRED","Verification link has expired.");
    if (!Hashing.sha256(token).equals(row.getTokenHash())) throw new Exceptions.BadRequest("TOKEN_INVALID","Invalid verification link.");
    row.setUsed(true); emailTokens.save(row);
    user.setEmailVerifiedAt(LocalDateTime.now());
  }
  @Transactional
  public void sendPasswordResetOtp(User user){
    String otp = newOtp();
    PasswordResetToken row = new PasswordResetToken();
    row.setUserId(user.getId()); row.setOtpHash(Hashing.sha256(otp)); row.setAttempts(0); row.setUsed(false);
    row.setExpiresAt(LocalDateTime.now().plusMinutes(15));
    resetTokens.save(row);
    // Same rule: delivery failure is logged, never thrown — the reset
    // request endpoint must keep its always-200 anti-enumeration contract
    // even when the mail provider is down.
    try { email.sendOtp(user.getEmail(), otp); }
    catch (Exception e){ log.error("Password reset code send failed for user {}: {}", user.getId(), e.getMessage()); }
  }
  @Transactional
  public void verifyPasswordResetOtp(User user, String otp){
    PasswordResetToken row = resetTokens.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
      .orElseThrow(() -> new Exceptions.BadRequest("OTP_NOT_FOUND","No active reset code."));
    if (row.getExpiresAt().isBefore(LocalDateTime.now())) throw new Exceptions.BadRequest("OTP_EXPIRED","Reset code has expired.");
    if (row.getAttempts()>=5) throw new Exceptions.BadRequest("OTP_ATTEMPTS_EXCEEDED","Too many wrong attempts.");
    if (!Hashing.sha256(otp).equals(row.getOtpHash())){
      // Atomic REQUIRES_NEW increment (D-103) — see verifyPhoneOtp.
      resetTokens.incrementAttempts(row.getId());
      throw new Exceptions.BadRequest("OTP_INVALID","Incorrect code");
    }
    row.setUsed(true); resetTokens.save(row);
  }
}
