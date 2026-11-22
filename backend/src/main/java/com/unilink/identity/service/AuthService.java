package com.unilink.identity.service;
import com.unilink.common.Exceptions; import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*; import com.unilink.identity.dto.AuthDtos.*;
import com.unilink.identity.repo.*; import com.unilink.security.LoginThrottle;
import com.unilink.storage.ExifStripper;
import com.unilink.storage.StorageGateway; import com.unilink.storage.UploadValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.*; import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException; import java.time.Duration; import java.time.LocalDateTime; import java.util.LinkedHashMap; import java.util.Map;
@Service
public class AuthService {
  private final UserRepository users; private final UserCredentialRepository creds;
  private final UniversityRepository universities; private final DepartmentRepository departments;
  private final BatchRepository batches; private final VerificationRequestRepository requests;
  private final VerificationAttemptRepository attempts; private final ProfileRepository profiles;
  private final EmailVerificationTokenRepository emailTokenRepo;
  private final LoginAttemptRepository loginAttempts;
  private final StorageGateway storage; private final UploadValidator uploads; private final OtpService otp;
  private final PasswordEncoder encoder; private final LoginThrottle throttle;
  private final AuthenticationManager authManager; private final SessionService sessions;
  private final OcrJobService ocrJob; private final AppProperties props;
  public AuthService(UserRepository users, UserCredentialRepository creds, UniversityRepository universities,
      DepartmentRepository departments, BatchRepository batches, VerificationRequestRepository requests,
      VerificationAttemptRepository attempts, ProfileRepository profiles, EmailVerificationTokenRepository emailTokenRepo, StorageGateway storage,
      UploadValidator uploads, OtpService otp, PasswordEncoder encoder, LoginThrottle throttle,
      @Qualifier("studentAuthManager") AuthenticationManager authManager, SessionService sessions,
      OcrJobService ocrJob, AppProperties props, LoginAttemptRepository loginAttempts){
    this.users=users; this.creds=creds; this.universities=universities; this.departments=departments; this.emailTokenRepo=emailTokenRepo;
    this.batches=batches; this.requests=requests; this.attempts=attempts; this.profiles=profiles;
    this.storage=storage; this.uploads=uploads; this.otp=otp; this.encoder=encoder; this.throttle=throttle;
    this.authManager=authManager; this.sessions=sessions; this.ocrJob=ocrJob; this.props=props;
    this.loginAttempts=loginAttempts;
  }
  /** Temporary passwords (FORCE_RESET, D-095) are valid for 24 hours from
   *  issue. Shared with AdminService/AdminAuthService so every issuer uses
   *  the same window. */
  public static final Duration TEMP_PASSWORD_VALIDITY = Duration.ofHours(24);
  static void checkPasswordRule(String pw){
    if (pw==null || pw.length()<8 || !pw.matches(".*[A-Z].*") || !pw.matches(".*[a-z].*") || !pw.matches(".*\\d.*"))
      throw new Exceptions.BadRequest("WEAK_PASSWORD","Password must be at least 8 characters with upper-case, lower-case and a digit (FR-AUTH-03)");
  }
  private static byte[] bytes(MultipartFile f){
    try { return f==null?new byte[0]:f.getBytes(); } catch (IOException e){ throw new Exceptions.BadRequest("INVALID_FILE","Cannot read uploaded file"); }
  }
  @Transactional
  public UserDto register(RegisterForm form){
    checkPasswordRule(form.getPassword());
    var uni = universities.findById(form.getUniversityId()).orElseThrow(() -> new Exceptions.NotFound("University"));
    if (!Boolean.TRUE.equals(uni.getActive())) throw new Exceptions.BadRequest("UNIVERSITY_INACTIVE","University is not active");
    var dept = departments.findById(form.getDepartmentId()).orElseThrow(() -> new Exceptions.NotFound("Department"));
    if (!dept.getUniversityId().equals(uni.getId())) throw new Exceptions.BadRequest("DEPARTMENT_MISMATCH","Department does not belong to the university");
    Long batchId = form.getBatchId();
    if (batchId!=null){ var b = batches.findById(batchId).orElseThrow(() -> new Exceptions.NotFound("Batch"));
      if (!b.getUniversityId().equals(uni.getId())) throw new Exceptions.BadRequest("BATCH_MISMATCH","Batch does not belong to the university"); }
    if (users.existsByEmail(form.getEmail())) throw new Exceptions.Conflict("EMAIL_TAKEN","An account with this email already exists");
    if (users.existsByUniversityIdAndStudentId(uni.getId(), form.getStudentId()))
      throw new Exceptions.Conflict("STUDENT_ID_TAKEN","This student ID is already registered at this university");
    // I-17 / D-077: a phone attached to a suspended or banned account cannot
    // verify a new account. Other duplicates are allowed but flagged to
    // reviewers in the verification queue (AdminService.verificationQueue).
    boolean phoneBlocked = users.findByPhone(form.getPhone()).stream()
      .anyMatch(other -> "SUSPENDED".equals(other.getState()) || "BANNED".equals(other.getState()));
    if (phoneBlocked) throw new Exceptions.Forbidden("This phone number is attached to a suspended or banned account");
    if (form.getIdCard()==null || form.getIdCard().isEmpty()) throw new Exceptions.BadRequest("ID_CARD_REQUIRED","Student ID card image is required (FR-AUTH-01)");
    if (form.getProfilePhoto()==null || form.getProfilePhoto().isEmpty()) throw new Exceptions.BadRequest("PROFILE_PHOTO_REQUIRED","Profile photo is required (FR-AUTH-01)");
    byte[] idBytes = bytes(form.getIdCard()); byte[] photoBytes = bytes(form.getProfilePhoto());
    // D-106: validate the ORIGINAL bytes (size is enforced pre-strip), then
    // strip EXIF; the stored bytes are the stripped ones. The sniffed type is
    // stored, not a hardcoded "image/jpeg" — a PNG/WebP upload is a PNG/WebP.
    String idType = uploads.validateImage(idBytes, UploadValidator.ID_CARD_MAX, form.getIdCard().getContentType());
    String photoType = uploads.validateImage(photoBytes, UploadValidator.PHOTO_MAX, form.getProfilePhoto().getContentType());
    String idKey = storage.store("id-cards", UploadValidator.sanitizeFileName(form.getIdCard().getOriginalFilename()), idType, ExifStripper.strip(idBytes));
    // ID card back (2026-11-22): optional second image, validated and
    // stored privately exactly like the front; older clients that send
    // only the front keep working.
    String idBackKey = null;
    if (form.getIdCardBack()!=null && !form.getIdCardBack().isEmpty()){
      byte[] backBytes = bytes(form.getIdCardBack());
      String backType = uploads.validateImage(backBytes, UploadValidator.ID_CARD_MAX, form.getIdCardBack().getContentType());
      idBackKey = storage.store("id-cards", UploadValidator.sanitizeFileName(form.getIdCardBack().getOriginalFilename()), backType, ExifStripper.strip(backBytes));
    }
    String photoKey = storage.store("profile-photos", UploadValidator.sanitizeFileName(form.getProfilePhoto().getOriginalFilename()), photoType, ExifStripper.strip(photoBytes));
    User u = new User();
    u.setUniversityId(uni.getId()); u.setDepartmentId(dept.getId()); u.setBatchId(batchId);
    u.setFullName(form.getFullName()); u.setEmail(form.getEmail()); u.setPhone(form.getPhone());
    u.setAddress(form.getAddress()); u.setStudentId(form.getStudentId());
    u.setState(AccountState.REGISTERED.name()); u.setIdCardKey(idKey); u.setIdCardBackKey(idBackKey); u.setProfilePhotoKey(photoKey);
    u = users.save(u);
    UserCredential c = new UserCredential(); c.setId(u.getId()); c.setPasswordHash(encoder.encode(form.getPassword())); creds.save(c);
    Profile p = new Profile(); p.setId(u.getId()); p.setPhoneVisible(false); profiles.save(p);
    VerificationRequest req = new VerificationRequest();
    req.setUserId(u.getId()); req.setStatus("AWAITING_VERIFICATION"); req.setOcrStatus("PENDING");
    req.setIdCardKey(idKey); req.setProfilePhotoKey(photoKey);
    req = requests.save(req);
    otp.sendPhoneOtp(u);
    // Seed the resend cooldown from the registration send, so the 60 s
    // cooldown (D-081) applies from the first send, not just from a resend.
    throttle.tryAcquire("otp-resend:"+u.getId(), 5, OTP_RESEND_WINDOW, OTP_RESEND_WINDOW);
    // The registration send is an OTP send to this phone, so it also counts
    // towards the per-phone cap (D-099): registering many accounts on one
    // phone must not bypass the resend cap. Best-effort like the seed
    // above — registration itself is never blocked by the cap (D-077 soft
    // uniqueness: a shared family phone must still be able to sign up).
    throttle.tryAcquire("otp-resend-phone:"+u.getPhone(), 5, OTP_RESEND_WINDOW, OTP_RESEND_WINDOW);
    otp.sendEmailVerificationLink(u, props.baseUrl());
    ocrJob.runOcr(req.getId(), idKey, u.getFullName(), u.getStudentId(), uni.getName());
    return toDto(u);
  }
  public static UserDto toDto(User u){
    return toDto(u, false);
  }
  /** Flag-aware DTO: login and /me report whether this account must change
   *  its password (D-094). Other flows (register, verify, resubmit) create
   *  no restricted session, so they use the flag-free form above. */
  public static UserDto toDto(User u, boolean mustChangePassword){
    return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getUniversityId(), u.getDepartmentId(),
      u.getBatchId(), u.getStudentId(), u.getState(), u.getPhoneVerifiedAt()!=null, u.getEmailVerifiedAt()!=null, u.getCreatedAt(), mustChangePassword);
  }
  /** D-094: whether the stored credential carries the must-change flag. */
  public boolean mustChangePassword(Long userId){
    return creds.findById(userId).map(c -> Boolean.TRUE.equals(c.getMustChangePassword())).orElse(false);
  }
  public User getUserOr404(Long id){ return users.findById(id).orElseThrow(() -> new Exceptions.NotFound("User")); }
  @Transactional
  public UserDto verifyPhone(OtpVerifyRequest r){
    User u = getUserOr404(r.userId());
    otp.verifyPhoneOtp(u, r.otp());
    users.save(u); maybeMoveToPendingReview(u); return toDto(u);
  }
  /** OTP resend caps (FR-AUTH-04 / NFR-SEC-05): 60 s cooldown per user,
   *  at most 5 resends per hour per user, at most 5 per hour per phone
   *  (D-099 — a phone shared by several accounts under D-077 soft
   *  uniqueness must not let each account resend freely), at most 10 per
   *  hour per IP. The three caps are acquired atomically
   *  (LoginThrottle.tryAcquireAll, D-101): a resend that hits one cap
   *  consumes none of the others. Reuses the LoginThrottle bucket pattern;
   *  no new table.
   *  Anti-enumeration: unknown users and already-verified phones get the
   *  same generic 200 as a real resend, and no OTP is sent and no
   *  rate-limit quota is consumed for them — the response leaks neither
   *  existence nor verification state. */
  private static final Duration OTP_RESEND_COOLDOWN = Duration.ofMinutes(1);
  private static final Duration OTP_RESEND_WINDOW = Duration.ofHours(1);
  @Transactional
  public Map<String,String> resendPhoneOtp(Long userId, String ip){
    User u = users.findById(userId).orElse(null);
    if (u==null || u.getPhoneVerifiedAt()!=null) return Map.of("status","OTP resent");
    String userKey = "otp-resend:"+userId;
    String phoneKey = u.getPhone()==null || u.getPhone().isBlank() ? null : "otp-resend-phone:"+u.getPhone();
    String ipKey = "otp-resend-ip:"+ip;
    // Pre-checks give the specific cooldown message; the atomic acquire
    // below re-validates everything, so a concurrent resend cannot slip
    // through between the check and the consume.
    if (throttle.isLocked(userKey) || (phoneKey!=null && throttle.isLocked(phoneKey)) || throttle.isLocked(ipKey))
      throw new Exceptions.TooManyRequests("Too many OTP requests. Try again later.");
    if (throttle.isCoolingDown(userKey, OTP_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Please wait a minute before requesting another OTP.");
    Map<String,Integer> caps = new LinkedHashMap<>();
    caps.put(userKey, 5);
    if (phoneKey!=null) caps.put(phoneKey, 5);
    caps.put(ipKey, 10);
    if (!throttle.tryAcquireAll(caps, OTP_RESEND_WINDOW, OTP_RESEND_WINDOW, userKey, OTP_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Too many OTP requests. Try again later.");
    otp.sendPhoneOtp(u);
    // Deliberately the same shape as the generic response above (no channel):
    // a differing body would re-introduce the existence oracle.
    return Map.of("status","OTP resent");
  }
  @Transactional
  public UserDto verifyEmail(String token, Long userId){
    User u = getUserOr404(userId);
    otp.verifyEmailToken(u, token);
    users.save(u); maybeMoveToPendingReview(u); return toDto(u);
  }
  /** Email link carries only the token; resolve the user from the token hash.
   *  D-148: this used to scan the whole email_tokens table (findAll) on
   *  every link click. Behaviour is unchanged: the row is still the newest
   *  unused token for that hash, and verifyEmail below still enforces
   *  that it is the user's newest unused token. */
  @Transactional
  public UserDto verifyEmailByToken(String token){
    var row = emailTokenRepo.findTopByTokenHashAndUsedFalseOrderByCreatedAtDesc(com.unilink.common.Hashing.sha256(token))
      .orElseThrow(() -> new Exceptions.BadRequest("TOKEN_NOT_FOUND","No active verification link."));
    return verifyEmail(token, row.getUserId());
  }
  private void maybeMoveToPendingReview(User u){
    if (u.getPhoneVerifiedAt()!=null && u.getEmailVerifiedAt()!=null && "REGISTERED".equals(u.getState())){
      u.setState(AccountState.REGISTERED.transitionTo(AccountState.PENDING_REVIEW).name());
      users.save(u);
      requests.findTopByUserIdOrderBySubmittedAtDesc(u.getId()).ifPresent(req -> {
        if ("AWAITING_VERIFICATION".equals(req.getStatus())){ req.setStatus("PENDING_REVIEW"); requests.save(req); }
      });
    }
  }
  public Authentication login(String email, String password, String ip){
    if (throttle.isLocked("login:"+email)) throw new Exceptions.TooManyRequests("Too many failed logins. Try again in 15 minutes.");
    try {
      Authentication auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
      var principal = (com.unilink.security.Principals.UnilinkUserPrincipal) auth.getPrincipal();
      if ("SUSPENDED".equals(principal.state()) || "BANNED".equals(principal.state()))
        throw new Exceptions.Forbidden("Account is "+principal.state().toLowerCase()+". Use the appeal link sent to your email (D-019).");
      // D-095: a temporary password is only good for 24 h. The password was
      // correct (authentication already succeeded), so this is a BadRequest
      // with a clear message, not a generic invalid-credentials failure —
      // and it is neither a success (no throttle reset) nor a wrong-password
      // failure (no lockout progress). The admin must re-issue.
      UserCredential cred = creds.findById(principal.userId()).orElse(null);
      if (cred!=null && cred.getTempPasswordExpiresAt()!=null && cred.getTempPasswordExpiresAt().isBefore(LocalDateTime.now()))
        throw new Exceptions.BadRequest("TEMP_PASSWORD_EXPIRED","Your temporary password has expired. Ask an administrator to issue a new one.");
      throttle.recordSuccess("login:"+email);
      recordLoginAttempt(email, ip, true);
      return auth;
    } catch (Exceptions.AppException e){
      // D-121: SUSPENDED/BANNED and TEMP_PASSWORD_EXPIRED rejections are
      // login attempts too — without this they left no login_attempts row,
      // so failed-login forensics (I-18: one row per login attempt) had a
      // hole exactly for the attempts most worth reviewing.
      recordLoginAttempt(email, ip, false);
      throw e;
    }
      catch (org.springframework.security.core.AuthenticationException e){
      throttle.recordFailure("login:"+email);
      recordLoginAttempt(email, ip, false);
      // D-139: was BadRequest (400). A failed sign-in is an authentication
      // failure — 401. Both login pages already branch on 401
      // (Login.tsx / AdminLogin.tsx: `status===401 ? 'Incorrect email or
      // password…'`); with 400 that branch was dead code. Code and message
      // are unchanged, so the enumeration behaviour (identical error for
      // unknown email vs wrong password) is unchanged. The wrong-current-
      // password check in changePassword stays 400: that is an authenticated
      // request-validation failure, not a sign-in attempt.
      throw new Exceptions.AppException(org.springframework.http.HttpStatus.UNAUTHORIZED,
        "INVALID_CREDENTIALS", "Email or password is incorrect"); // generic error (FR-AUTH-10)
    }
  }
  /** I-18: login_attempts is written, not dead — one row per login attempt,
   *  so failed-login forensics has a record. A write failure here must never
   *  break login itself. */
  private void recordLoginAttempt(String email, String ip, boolean success){
    try {
      LoginAttempt row = new LoginAttempt();
      row.setEmail(email); row.setIp(ip); row.setSuccess(success);
      loginAttempts.save(row);
    } catch (Exception ignored){ /* audit-only; login outcome stands */ }
  }
  /** Password-reset OTP requests are rate-limited like other OTP sends
   *  (NFR-SEC-05, D-102): at most 5 per hour per email and 10 per hour
   *  per IP. A capped request is dropped SILENTLY — the caller still gets
   *  the generic "If the email exists…" response, so the cap is not an
   *  oracle for whether the email exists or how many resets it had. */
  private static final Duration PASSWORD_RESET_WINDOW = Duration.ofHours(1);
  @Transactional
  public void requestPasswordReset(String email, String ip){
    Map<String,Integer> caps = new LinkedHashMap<>();
    caps.put("password-reset:"+email, 5);
    caps.put("password-reset-ip:"+ip, 10);
    if (!throttle.tryAcquireAll(caps, PASSWORD_RESET_WINDOW, PASSWORD_RESET_WINDOW)) return;
    users.findByEmail(email).ifPresent(u -> { if (!"DELETED".equals(u.getState())) otp.sendPasswordResetOtp(u); });
    // Always succeed silently: never reveal whether the email exists.
  }
  @Transactional
  public void confirmPasswordReset(PasswordResetConfirm r){
    checkPasswordRule(r.newPassword());
    User u = users.findByEmail(r.email()).orElseThrow(() -> new Exceptions.BadRequest("INVALID_CREDENTIALS","Email or code is incorrect"));
    otp.verifyPasswordResetOtp(u, r.otp());
    UserCredential c = creds.findById(u.getId()).orElseThrow(() -> new Exceptions.NotFound("User"));
    c.setPasswordHash(encoder.encode(r.newPassword()));
    // A password reset is a password change: it clears any must-change flag
    // and temporary-password expiry (D-094/D-095).
    c.setMustChangePassword(false); c.setTempPasswordExpiresAt(null); creds.save(c);
    sessions.invalidateAllSessions(u.getEmail());
  }
  @Transactional
  public void changePassword(Long userId, ChangePasswordRequest r){
    checkPasswordRule(r.newPassword());
    User u = getUserOr404(userId);
    UserCredential c = creds.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (!encoder.matches(r.oldPassword(), c.getPasswordHash())) throw new Exceptions.BadRequest("INVALID_CREDENTIALS","Current password is incorrect");
    c.setPasswordHash(encoder.encode(r.newPassword()));
    // Changing the password satisfies the must-change requirement (D-094)
    // and retires any temporary-password expiry (D-095).
    c.setMustChangePassword(false); c.setTempPasswordExpiresAt(null); creds.save(c);
    sessions.invalidateAllSessions(u.getEmail());
  }
  @Transactional
  public UserDto resubmit(Long userId, MultipartFile idCard){
    User u = getUserOr404(userId);
    if (!"REJECTED".equals(u.getState())) throw new Exceptions.BadRequest("NOT_REJECTED","Only rejected accounts can resubmit");
    LocalDateTime since = LocalDateTime.now().minusHours(24);
    // I-18: count RESUBMIT outcomes only (D-012). APPROVED/REJECTED decision
    // rows in the same table must not consume the 3-per-24h resubmit cap.
    long recent = attempts.countByUserIdAndOutcomeAndCreatedAtAfter(userId, "RESUBMIT", since);
    if (recent >= 3) throw new Exceptions.TooManyRequests("Resubmission limit reached (3 per 24 hours). Try again later.");
    String idKey = u.getIdCardKey();
    if (idCard!=null && !idCard.isEmpty()){
      byte[] b = bytes(idCard);
      String type = uploads.validateImage(b, UploadValidator.ID_CARD_MAX, idCard.getContentType());
      // D-106: strip EXIF before storing. The previous key is NOT deleted here:
      // the rejected attempt's VerificationRequest row still references it, and
      // D-053 keeps rejected ID images for 30 days to support appeal; the
      // purgeIdImages job deletes it when retention expires.
      idKey = storage.store("id-cards", UploadValidator.sanitizeFileName(idCard.getOriginalFilename()), type, ExifStripper.strip(b));
      u.setIdCardKey(idKey);
    }
    u.setState(AccountState.REJECTED.transitionTo(AccountState.PENDING_REVIEW).name());
    users.save(u);
    VerificationRequest req = new VerificationRequest();
    req.setUserId(userId); req.setStatus("PENDING_REVIEW"); req.setOcrStatus("PENDING"); req.setIdCardKey(idKey);
    req.setProfilePhotoKey(u.getProfilePhotoKey());
    req = requests.save(req);
    VerificationAttempt att = new VerificationAttempt();
    att.setUserId(userId); att.setVerificationRequestId(req.getId()); att.setOutcome("RESUBMIT"); attempts.save(att);
    String uniName = universities.findById(u.getUniversityId()).map(University::getName).orElse(null);
    ocrJob.runOcr(req.getId(), idKey, u.getFullName(), u.getStudentId(), uniName);
    return toDto(u);
  }
  @Transactional(readOnly=true)
  public VerificationStatusDto verificationStatus(Long userId){
    User u = getUserOr404(userId);
    var req = requests.findTopByUserIdOrderBySubmittedAtDesc(userId).orElse(null);
    return new VerificationStatusDto(userId, u.getState(), req==null?null:req.getStatus(),
      req==null?null:req.getOcrMatchScore(), req==null?null:req.getOcrStatus(), req==null?null:req.getReason());
  }
}
