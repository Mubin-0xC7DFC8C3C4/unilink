package com.unilink.identity.service;
import com.unilink.common.Exceptions; import com.unilink.common.Hashing;
import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.integration.email.EmailProvider; import com.unilink.integration.sms.SmsProvider;
import com.unilink.security.LoginThrottle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom; import java.time.Duration; import java.time.LocalDateTime; import java.util.LinkedHashMap; import java.util.Map; import java.util.UUID;
/** OTP (6-digit, 5 min, 5 attempts, hashed, single-use) + email link (24 h, hashed, single-use) — D-011.
 *  D-174: email OTP codes are 6 digits, hashed, single-use, 10-minute
 *  expiry — sent only after OCR reaches AUTO_VERIFIED. */
@Service
public class OtpService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OtpService.class);
  private final PhoneOtpRepository phoneOtps; private final EmailVerificationTokenRepository emailTokens;
  private final PasswordResetTokenRepository resetTokens; private final EmailOtpRepository emailOtps;
  private final SmsProvider sms; private final EmailProvider email;
  private final AppProperties props; private final LoginThrottle throttle;
  private static final SecureRandom RANDOM = new SecureRandom();
  public OtpService(PhoneOtpRepository p, EmailVerificationTokenRepository e, PasswordResetTokenRepository r,
                    EmailOtpRepository emailOtps, SmsProvider sms, EmailProvider email,
                    AppProperties props, LoginThrottle throttle){
    phoneOtps=p; emailTokens=e; resetTokens=r; this.emailOtps=emailOtps; this.sms=sms; this.email=email;
    this.props=props; this.throttle=throttle;
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
      try { email.sendOtp(user.getEmail(), otp, 5); } // fallback channel, same OTP record
      // Both channels down: a clean 503, not a 500 — the user can retry
      // the send and the OTP is never silently lost.
      catch (Exception emailEx){ throw new Exceptions.ServiceUnavailable("We could not send your verification code. Please try again in a moment."); }
      return "EMAIL";
    }
  }
  @Transactional
  public void verifyPhoneOtp(User user, String otp){
    PhoneOtp row = phoneOtps.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
      .orElseThrow(() -> new Exceptions.BadRequest("OTP_NOT_FOUND","No active OTP. Request a new one."));
    checkPhoneOtpRow(row, otp);
    user.setPhoneVerifiedAt(LocalDateTime.now());
  }
  /** Shared expiry / attempts / hash check for phone OTP rows (D-103:
   *  the failed-guess increment runs in its own transaction so the cap
   *  engages even though the caller's transaction rolls back on
   *  OTP_INVALID). Marks the row used on success. */
  private void checkPhoneOtpRow(PhoneOtp row, String otp){
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
  }
  /** Whether a real SMS gateway is configured (app.sms.provider=gateway).
   *  The log provider only writes the OTP to the application log — that is
   *  not delivery, so it can never prove ownership of a new number (D-199). */
  public boolean smsGatewayConfigured(){
    return props.sms()!=null && "gateway".equalsIgnoreCase(props.sms().provider());
  }
  /** Phone-change OTP (D-199): SMS-channel ONLY — the email fallback in
   *  {@link #sendPhoneOtp} must NOT count as phone proof, so there is no
   *  fallback here at all. If no SMS gateway is configured, or the gateway
   *  call fails, this throws a clear 503 and no OTP row is left behind (the
   *  caller's transaction rolls back). Reuses the 6-digit / 5-min / hashed /
   *  single-use row and the resend caps (60 s cooldown, 5/hour/user,
   *  5/hour/phone, 10/hour/IP — AuthService.resendPhoneOtp shape), keyed on
   *  the NEW phone so a shared number cannot be spammed per account. */
  private static final Duration PHONE_CHANGE_RESEND_COOLDOWN = Duration.ofMinutes(1);
  private static final Duration PHONE_CHANGE_RESEND_WINDOW = Duration.ofHours(1);
  @Transactional
  public void sendPhoneOtpTo(User user, String newPhone, String ip){
    if (!smsGatewayConfigured())
      throw new Exceptions.AppException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_NOT_CONFIGURED",
        "Phone change by SMS is not available yet — no SMS gateway is configured. Ask an administrator to configure one.");
    String userKey = "phone-change:"+user.getId();
    String phoneKey = "phone-change-phone:"+newPhone;
    String ipKey = ip==null || ip.isBlank() ? null : "phone-change-ip:"+ip;
    // Pre-checks give the specific cooldown message; the atomic acquire
    // below re-validates everything, so a concurrent send cannot slip
    // through between the check and the consume.
    if (throttle.isLocked(userKey) || throttle.isLocked(phoneKey) || (ipKey!=null && throttle.isLocked(ipKey)))
      throw new Exceptions.TooManyRequests("Too many OTP requests. Try again later.");
    if (throttle.isCoolingDown(userKey, PHONE_CHANGE_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Please wait a minute before requesting another OTP.");
    Map<String,Integer> caps = new LinkedHashMap<>();
    caps.put(userKey, 5);
    caps.put(phoneKey, 5);
    if (ipKey!=null) caps.put(ipKey, 10);
    if (!throttle.tryAcquireAll(caps, PHONE_CHANGE_RESEND_WINDOW, PHONE_CHANGE_RESEND_WINDOW, userKey, PHONE_CHANGE_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Too many OTP requests. Try again later.");
    String otp = newOtp();
    PhoneOtp row = new PhoneOtp();
    row.setUserId(user.getId()); row.setPhone(newPhone); row.setOtpHash(Hashing.sha256(otp));
    row.setChannel("SMS"); row.setAttempts(0); row.setUsed(false);
    row.setExpiresAt(LocalDateTime.now().plusMinutes(5));
    phoneOtps.save(row);
    try { sms.sendOtp(newPhone, otp); }
    catch (SmsProvider.ProviderException ex){
      // SMS-only: no email fallback (D-199). A clean 503, not a 500 —
      // the user can retry, and the OTP row rolls back with the caller.
      throw new Exceptions.ServiceUnavailable("We could not send your verification code. Please try again in a moment.");
    }
  }
  /** Verifies a phone-change OTP (D-199): succeeds only against the newest
   *  unused OTP for this user AND only when that OTP was issued for the
   *  given (pending) number — a code sent to a different number never
   *  verifies. Does NOT touch the user: the caller swaps the phone in the
   *  same transaction, so the old number stays in force on any failure. */
  @Transactional
  public void verifyPhoneOtpFor(User user, String phone, String otp){
    PhoneOtp row = phoneOtps.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
      .orElseThrow(() -> new Exceptions.BadRequest("OTP_NOT_FOUND","No active OTP. Request a new one."));
    if (phone==null || !phone.equals(row.getPhone()))
      throw new Exceptions.BadRequest("OTP_INVALID","This code was not sent to that number. Request a new one.");
    checkPhoneOtpRow(row, otp);
  }
  /** Issues (and sends) a fresh verification link. Returns true only when
   *  the provider accepted the message — the caller reports this to the
   *  frontend so a failed send is visible and re-sendable instead of
   *  silently lost. A failed send still must not abort registration: the
   *  token is stored and the link can be re-sent. (Production 2026-11-23:
   *  an unreachable SMTP route otherwise destroyed signups entirely.)
   *  Re-issuing retires earlier unused links, so only the newest link in
   *  the mailbox works. The token itself is never logged. */
  @Transactional
  public boolean sendEmailVerificationLink(User user, String baseUrl){
    String token = newToken();
    emailTokens.findByUserIdAndUsedFalse(user.getId()).forEach(old -> { old.setUsed(true); emailTokens.save(old); });
    EmailVerificationToken row = new EmailVerificationToken();
    row.setUserId(user.getId()); row.setTokenHash(Hashing.sha256(token)); row.setUsed(false);
    row.setExpiresAt(LocalDateTime.now().plusHours(24));
    emailTokens.save(row);
    try { email.sendVerificationLink(user.getEmail(), baseUrl + "/api/auth/verify-email?token=" + token); return true; }
    catch (Exception e){ log.error("Email verification link send failed for user {}: {}", user.getId(), e.getMessage()); return false; }
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
    try { email.sendOtp(user.getEmail(), otp, 15); }
    catch (Exception e){ log.error("Password reset code send failed for user {}: {}", user.getId(), e.getMessage()); }
  }
  /** Email OTP code (D-174): 6 digits, 10-minute expiry, hashed,
   *  single-use. Issuing retires earlier unused codes, so only the
   *  newest code in the inbox works. Returns true only when the provider
   *  accepted the message — a failed send never throws, so the caller
   *  (the OCR job) is not rolled back by a mail outage; the frontend
   *  offers a resend instead. The code itself is never logged. */
  @Transactional
  public boolean sendEmailOtp(User user){
    String otp = newOtp();
    emailOtps.findByUserIdAndUsedFalse(user.getId()).forEach(old -> { old.setUsed(true); emailOtps.save(old); });
    EmailOtp row = new EmailOtp();
    row.setUserId(user.getId()); row.setOtpHash(Hashing.sha256(otp)); row.setAttempts(0); row.setUsed(false);
    row.setExpiresAt(LocalDateTime.now().plusMinutes(10));
    emailOtps.save(row);
    try { email.sendOtp(user.getEmail(), otp, 10); return true; }
    catch (Exception e){ log.error("Email OTP send failed for user {}: {}", user.getId(), e.getMessage()); return false; }
  }
  /** Whether an unused, unexpired email OTP exists for the user — the
   *  registration-status response reports this as emailOtpSent. */
  @Transactional(readOnly = true)
  public boolean hasActiveEmailOtp(Long userId){
    return emailOtps.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(userId)
      .map(row -> row.getExpiresAt() != null && row.getExpiresAt().isAfter(LocalDateTime.now())).orElse(false);
  }
  @Transactional
  public void verifyEmailOtp(User user, String otp){
    EmailOtp row = emailOtps.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
      .orElseThrow(() -> new Exceptions.BadRequest("OTP_NOT_FOUND","No active verification code. Request a new one."));
    if (row.getExpiresAt().isBefore(LocalDateTime.now())) throw new Exceptions.BadRequest("OTP_EXPIRED","Verification code has expired. Request a new one.");
    if (row.getAttempts()>=5) throw new Exceptions.BadRequest("OTP_ATTEMPTS_EXCEEDED","Too many wrong attempts. Request a new verification code.");
    if (!Hashing.sha256(otp).equals(row.getOtpHash())){
      // Atomic REQUIRES_NEW increment (D-103): the OTP_INVALID exception
      // below rolls back this method's transaction, which used to roll
      // back the attempt count as well — the cap could never engage.
      emailOtps.incrementAttempts(row.getId());
      throw new Exceptions.BadRequest("OTP_INVALID","Incorrect code");
    }
    row.setUsed(true); emailOtps.save(row);
    user.setEmailVerifiedAt(LocalDateTime.now());
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
