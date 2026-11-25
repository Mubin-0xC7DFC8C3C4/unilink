package com.unilink.identity.service;
import com.unilink.common.Exceptions; import com.unilink.common.Hashing;
import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*; import com.unilink.identity.dto.AuthDtos.*;
import com.unilink.identity.event.DomainEvents;
import com.unilink.identity.repo.*; import com.unilink.security.LoginThrottle;
import com.unilink.storage.ExifStripper;
import com.unilink.storage.StorageGateway; import com.unilink.storage.UploadValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.*; import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException; import java.time.Duration; import java.time.LocalDateTime; import java.util.LinkedHashMap; import java.util.Map;
@Service
public class AuthService {
  private final UserRepository users; private final UserCredentialRepository creds;
  private final UniversityRepository universities; private final DepartmentRepository departments;
  private final DepartmentCatalogRepository catalogs;
  private final BatchRepository batches; private final VerificationRequestRepository requests;
  private final VerificationAttemptRepository attempts; private final ProfileRepository profiles;
  private final EmailVerificationTokenRepository emailTokenRepo;
  private final LoginAttemptRepository loginAttempts;
  private final StorageGateway storage; private final UploadValidator uploads; private final OtpService otp;
  private final PasswordEncoder encoder; private final LoginThrottle throttle;
  private final AuthenticationManager authManager; private final SessionService sessions;
  private final OcrJobService ocrJob; private final AppProperties props;
  private final ApplicationEventPublisher events;
  public AuthService(UserRepository users, UserCredentialRepository creds, UniversityRepository universities,
      DepartmentRepository departments, BatchRepository batches, VerificationRequestRepository requests,
      VerificationAttemptRepository attempts, ProfileRepository profiles, EmailVerificationTokenRepository emailTokenRepo, StorageGateway storage,
      UploadValidator uploads, OtpService otp, PasswordEncoder encoder, LoginThrottle throttle,
      @Qualifier("studentAuthManager") AuthenticationManager authManager, SessionService sessions,
      OcrJobService ocrJob, AppProperties props, LoginAttemptRepository loginAttempts,
      DepartmentCatalogRepository catalogs, ApplicationEventPublisher events){
    this.users=users; this.creds=creds; this.universities=universities; this.departments=departments; this.emailTokenRepo=emailTokenRepo;
    this.batches=batches; this.requests=requests; this.attempts=attempts; this.profiles=profiles;
    this.storage=storage; this.uploads=uploads; this.otp=otp; this.encoder=encoder; this.throttle=throttle;
    this.authManager=authManager; this.sessions=sessions; this.ocrJob=ocrJob; this.props=props;
    this.loginAttempts=loginAttempts; this.catalogs=catalogs; this.events=events;
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
  /** D-194: free-text batch/session. 1–32 characters, letters/digits/
   *  space/hyphen/slash/dot/parentheses, starting with a letter or digit.
   *  Stored verbatim — never normalised, truncated or converted. A blank
   *  value is allowed (batch is optional); the frontend and backend share
   *  this exact rule. */
  public static final java.util.regex.Pattern BATCH_PATTERN =
      java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 \\-/.()]{0,31}$");
  static String validateBatch(String batch){
    if (batch == null) return null;
    String v = batch.trim();
    if (v.isEmpty()) return null;
    if (!BATCH_PATTERN.matcher(v).matches())
      throw new Exceptions.BadRequest("INVALID_BATCH","Batch/session must be 1–32 characters: letters, digits, spaces, hyphens, slashes, dots or parentheses");
    return v;
  }
  /** D-193: free-text department label. 1–255 characters; the same
   *  character class as the batch, plus commas and ampersands for names
   *  like "Computer Science & Engineering". */
  static String validateDepartmentLabel(String label){
    if (label == null) return null;
    String v = label.trim();
    if (v.isEmpty()) return null;
    if (v.length() > 255 || !v.matches("^[A-Za-z0-9][A-Za-z0-9 \\-/.(),&]{0,254}$"))
      throw new Exceptions.BadRequest("INVALID_DEPARTMENT","Department must be 1–255 characters: letters, digits, spaces and - / . ( ) , &");
    return v;
  }
  @Transactional
  public UserDto register(RegisterForm form){
    checkPasswordRule(form.getPassword());
    var uni = universities.findById(form.getUniversityId()).orElseThrow(() -> new Exceptions.NotFound("University"));
    if (!Boolean.TRUE.equals(uni.getActive())) throw new Exceptions.BadRequest("UNIVERSITY_INACTIVE","University is not active");
    // D-193: department is a verified catalog row OR a free-text label —
    // exactly one. A catalog id must belong to the university; a label is
    // stored verbatim and the account goes to manual review for the
    // department (the matcher can only check it when it matches a known
    // department name). No department is ever invented for a university.
    Department dept = null; String departmentLabel = null;
    if (form.getDepartmentId() != null){
      dept = departments.findById(form.getDepartmentId()).orElseThrow(() -> new Exceptions.NotFound("Department"));
      if (!dept.getUniversityId().equals(uni.getId())) throw new Exceptions.BadRequest("DEPARTMENT_MISMATCH","Department does not belong to the university");
    } else {
      departmentLabel = validateDepartmentLabel(form.getDepartmentLabel());
      if (departmentLabel == null) throw new Exceptions.BadRequest("DEPARTMENT_REQUIRED","Department is required — choose one or type your department");
    }
    // D-194: free-text batch, validated and stored verbatim; the numeric
    // batchId is no longer part of registration (legacy rows keep theirs).
    String batchLabel = validateBatch(form.getBatch());
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
    if (form.getIdCardBack()==null || form.getIdCardBack().isEmpty()) throw new Exceptions.BadRequest("ID_CARD_BACK_REQUIRED","The back of the student ID card is required (FR-AUTH-01)");
    byte[] idBytes = bytes(form.getIdCard());
    byte[] backBytes = bytes(form.getIdCardBack());
    // D-190: the same image submitted as both sides is rejected, not
    // silently accepted — byte-identical uploads (SHA-256 of the raw
    // bytes) cannot be a front and a back at once. Different bytes that
    // OCR as the same side are caught by the matcher (D-191).
    if (java.util.Arrays.equals(Hashing.sha256Bytes(idBytes), Hashing.sha256Bytes(backBytes)))
      throw new Exceptions.BadRequest("ID_CARD_SAME_IMAGE","The front and back images are identical. Please upload the front and the back of your ID card.");
    // D-106: validate the ORIGINAL bytes (size is enforced pre-strip), then
    // strip EXIF; the stored bytes are the stripped ones. The sniffed type is
    // stored, not a hardcoded "image/jpeg" — a PNG/WebP upload is a PNG/WebP.
    String idType = uploads.validateImage(idBytes, UploadValidator.ID_CARD_MAX, form.getIdCard().getContentType());
    String idKey = storage.store("id-cards", UploadValidator.sanitizeFileName(form.getIdCard().getOriginalFilename()), idType, ExifStripper.strip(idBytes));
    // D-190: the back is required and stored privately exactly like the
    // front, in the same id-cards namespace, under its own key.
    String backType = uploads.validateImage(backBytes, UploadValidator.ID_CARD_MAX, form.getIdCardBack().getContentType());
    String idBackKey = storage.store("id-cards", UploadValidator.sanitizeFileName(form.getIdCardBack().getOriginalFilename()), backType, ExifStripper.strip(backBytes));
    // D-178/D-197: no profile photo is collected at registration —
    // students upload it from Settings → Edit Profile after logging in.
    // RegisterForm has no profilePhoto part any more (backend-enforced):
    // a photo part sent by an old client is ignored by binding, and
    // nothing is stored. User/VerificationRequest profilePhotoKey stay
    // null for a fresh registration.
    User u = new User();
    u.setUniversityId(uni.getId()); u.setDepartmentId(dept == null ? null : dept.getId()); u.setDepartmentLabel(departmentLabel);
    u.setBatchId(null); u.setBatchLabel(batchLabel);
    u.setFullName(form.getFullName()); u.setEmail(form.getEmail()); u.setPhone(form.getPhone());
    u.setAddress(form.getAddress()); u.setStudentId(form.getStudentId());
    u.setState(AccountState.REGISTERED.name()); u.setIdCardKey(idKey); u.setIdCardBackKey(idBackKey); u.setProfilePhotoKey(null);
    u = users.save(u);
    UserCredential c = new UserCredential(); c.setId(u.getId()); c.setPasswordHash(encoder.encode(form.getPassword())); creds.save(c);
    Profile p = new Profile(); p.setId(u.getId()); p.setPhoneVisible(false); profiles.save(p);
    VerificationRequest req = new VerificationRequest();
    req.setUserId(u.getId()); req.setStatus("AWAITING_VERIFICATION"); req.setOcrStatus("PENDING");
    req.setIdCardKey(idKey); req.setIdCardBackKey(idBackKey); req.setProfilePhotoKey(null);
    // D-175: the registration token scopes the status / email-OTP
    // endpoints. Only the hash is stored; the plaintext is returned once
    // in the register response. It expires after 24 hours.
    String verificationToken = OtpService.newToken();
    req.setVerificationTokenHash(Hashing.sha256(verificationToken));
    req.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24));
    req = requests.save(req);
    // D-173: the journey is OCR → email OTP code → active. No phone OTP
    // and no email link are sent at registration any more; the email OTP
    // is sent by the OCR job only when it reaches AUTO_VERIFIED.
    // D-191: the job OCRs both sides and cross-validates them.
    ocrJob.runOcr(req.getId(), idKey, idBackKey, ocrSubject(u, uni.getName(), dept, departmentLabel), batchLabel);
    return toDto(u, false, null, verificationToken);
  }
  /** The registration data the OCR matcher compares against the card.
   *  With a free-text department (D-193) the catalog short name is
   *  unknown, so the matcher checks the label as the department name. */
  private IdCardMatcher.Subject ocrSubject(User u, String universityName, Department dept, String departmentLabel){
    String deptName = dept != null ? dept.getName() : departmentLabel;
    String deptShort = dept == null ? null : catalogs.findById(dept.getCatalogId()).map(DepartmentCatalog::getShortName).orElse(null);
    return new IdCardMatcher.Subject(u.getFullName(), u.getStudentId(), universityName, deptName, deptShort, u.getAddress());
  }
  public static UserDto toDto(User u){
    return toDto(u, false);
  }
  /** Flag-aware DTO: login and /me report whether this account must change
   *  its password (D-094). Other flows (register, verify, resubmit) create
   *  no restricted session, so they use the flag-free form above. */
  public static UserDto toDto(User u, boolean mustChangePassword){
    return toDto(u, mustChangePassword, null);
  }
  /** emailSent is meaningful only at registration (null elsewhere): it
   *  tells the frontend whether the verification email actually went out,
   *  so a failed send is visible and re-sendable instead of silent. */
  public static UserDto toDto(User u, boolean mustChangePassword, Boolean emailSent){
    return toDto(u, mustChangePassword, emailSent, null);
  }
  /** verificationToken is meaningful only in the register / resubmit
   *  response (D-175): the plaintext registration token, returned once. */
  public static UserDto toDto(User u, boolean mustChangePassword, Boolean emailSent, String verificationToken){
    return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getUniversityId(), u.getDepartmentId(),
      u.getDepartmentLabel(), u.getBatchId(), u.getBatchLabel(), u.getStudentId(), u.getState(), u.getPhoneVerifiedAt()!=null, u.getEmailVerifiedAt()!=null, u.getCreatedAt(), mustChangePassword, emailSent, verificationToken);
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
  /** Resend of the email verification link (2026-11-24, real SMTP):
   *  the secure retry when the registration send failed or the link
   *  expired. Same shape as resendPhoneOtp — 60 s cooldown, at most 5
   *  resends per hour per user, per email and 10 per hour per IP, acquired
   *  atomically — and the same anti-enumeration contract: unknown users,
   *  unknown emails and already-verified emails all get the same generic
   *  200, send nothing and consume no quota. */
  @Transactional
  public Map<String,String> resendEmailVerificationLink(Long userId, String email, String ip){
    User u = null;
    if (userId!=null) u = users.findById(userId).orElse(null);
    else if (email!=null && !email.isBlank()) u = users.findByEmail(email.trim()).orElse(null);
    if (u==null || u.getEmailVerifiedAt()!=null) return Map.of("status","Verification email resent");
    String userKey = "email-resend:"+u.getId();
    String emailKey = u.getEmail()==null || u.getEmail().isBlank() ? null : "email-resend-email:"+u.getEmail().toLowerCase();
    String ipKey = "email-resend-ip:"+ip;
    if (throttle.isLocked(userKey) || (emailKey!=null && throttle.isLocked(emailKey)) || throttle.isLocked(ipKey))
      throw new Exceptions.TooManyRequests("Too many verification email requests. Try again later.");
    if (throttle.isCoolingDown(userKey, OTP_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Please wait a minute before requesting another verification email.");
    Map<String,Integer> caps = new LinkedHashMap<>();
    caps.put(userKey, 5);
    if (emailKey!=null) caps.put(emailKey, 5);
    caps.put(ipKey, 10);
    if (!throttle.tryAcquireAll(caps, OTP_RESEND_WINDOW, OTP_RESEND_WINDOW, userKey, OTP_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Too many verification email requests. Try again later.");
    otp.sendEmailVerificationLink(u, props.baseUrl());
    return Map.of("status","Verification email resent");
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
  public UserDto resubmit(Long userId, MultipartFile idCard, MultipartFile idCardBack){
    User u = getUserOr404(userId);
    if (!"REJECTED".equals(u.getState())) throw new Exceptions.BadRequest("NOT_REJECTED","Only rejected accounts can resubmit");
    LocalDateTime since = LocalDateTime.now().minusHours(24);
    // I-18: count RESUBMIT outcomes only (D-012). APPROVED/REJECTED decision
    // rows in the same table must not consume the 3-per-24h resubmit cap.
    long recent = attempts.countByUserIdAndOutcomeAndCreatedAtAfter(userId, "RESUBMIT", since);
    if (recent >= 3) throw new Exceptions.TooManyRequests("Resubmission limit reached (3 per 24 hours). Try again later.");
    String idKey = u.getIdCardKey();
    String idBackKey = u.getIdCardBackKey();
    byte[] newFrontBytes = null; byte[] newBackBytes = null;
    if (idCard!=null && !idCard.isEmpty()){
      newFrontBytes = bytes(idCard);
      String type = uploads.validateImage(newFrontBytes, UploadValidator.ID_CARD_MAX, idCard.getContentType());
      // D-197: a front-only re-upload must not be the stored back image
      // (the both-new check below only covers re-uploading both sides).
      if (idCardBack==null || idCardBack.isEmpty()) checkSingleSideNotSameImage(newFrontBytes, u.getIdCardBackKey());
      // D-106: strip EXIF before storing. The previous key is NOT deleted here:
      // the rejected attempt's VerificationRequest row still references it, and
      // D-053 keeps rejected ID images for 30 days to support appeal; the
      // purgeIdImages job deletes it when retention expires.
      idKey = storage.store("id-cards", UploadValidator.sanitizeFileName(idCard.getOriginalFilename()), type, ExifStripper.strip(newFrontBytes));
      u.setIdCardKey(idKey);
    }
    // D-190: the back behaves exactly like the front — a side that is not
    // re-uploaded is carried over from the stored key. A resubmission with
    // neither side is still a resubmission of the stored images.
    if (idCardBack!=null && !idCardBack.isEmpty()){
      newBackBytes = bytes(idCardBack);
      String type = uploads.validateImage(newBackBytes, UploadValidator.ID_CARD_MAX, idCardBack.getContentType());
      // D-197: mirror of the front-only check above — a back-only
      // re-upload must not be the stored front image.
      if (idCard==null || idCard.isEmpty()) checkSingleSideNotSameImage(newBackBytes, u.getIdCardKey());
      idBackKey = storage.store("id-cards", UploadValidator.sanitizeFileName(idCardBack.getOriginalFilename()), type, ExifStripper.strip(newBackBytes));
      u.setIdCardBackKey(idBackKey);
    }
    if (newFrontBytes != null && newBackBytes != null
        && java.util.Arrays.equals(Hashing.sha256Bytes(newFrontBytes), Hashing.sha256Bytes(newBackBytes)))
      throw new Exceptions.BadRequest("ID_CARD_SAME_IMAGE","The front and back images are identical. Please upload the front and the back of your ID card.");
    u.setState(AccountState.REJECTED.transitionTo(AccountState.PENDING_REVIEW).name());
    users.save(u);
    VerificationRequest req = new VerificationRequest();
    req.setUserId(userId); req.setStatus("PENDING_REVIEW"); req.setOcrStatus("PENDING"); req.setIdCardKey(idKey);
    req.setIdCardBackKey(idBackKey);
    req.setProfilePhotoKey(u.getProfilePhotoKey());
    // D-175: a fresh registration token for the new attempt, so the
    // student re-enters the verification flow (Step A) for this resubmission.
    String verificationToken = OtpService.newToken();
    req.setVerificationTokenHash(Hashing.sha256(verificationToken));
    req.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24));
    req = requests.save(req);
    VerificationAttempt att = new VerificationAttempt();
    att.setUserId(userId); att.setVerificationRequestId(req.getId()); att.setOutcome("RESUBMIT"); attempts.save(att);
    String uniName = universities.findById(u.getUniversityId()).map(University::getName).orElse(null);
    var dept = u.getDepartmentId() == null ? null : departments.findById(u.getDepartmentId()).orElse(null);
    ocrJob.runOcr(req.getId(), idKey, idBackKey, dept == null
      ? new IdCardMatcher.Subject(u.getFullName(), u.getStudentId(), uniName, u.getDepartmentLabel(), null, u.getAddress())
      : ocrSubject(u, uniName, dept, u.getDepartmentLabel()), u.getBatchLabel());
    return toDto(u, false, null, verificationToken);
  }
  /** D-197: a single re-uploaded side must not be byte-identical to the
   *  stored opposite side (the both-new check in resubmit only covers
   *  re-uploading both sides at once). The new bytes are EXIF-stripped
   *  first, because the stored bytes are the stripped ones — comparing
   *  raw new bytes against stripped stored bytes would miss the same
   *  image whenever the original carried EXIF. A missing stored key or
   *  an unreadable stored object means "cannot compare", not a failure:
   *  the resubmission proceeds and the matcher still sees the images. */
  private void checkSingleSideNotSameImage(byte[] newBytes, String storedOppositeKey){
    if (newBytes == null || storedOppositeKey == null) return;
    byte[] stored;
    try { stored = storage.open(storedOppositeKey).readAllBytes(); }
    catch (Exception e){ return; }
    if (stored == null) return;
    if (java.util.Arrays.equals(Hashing.sha256Bytes(ExifStripper.strip(newBytes)), Hashing.sha256Bytes(stored)))
      throw new Exceptions.BadRequest("ID_CARD_SAME_IMAGE","The front and back images are identical. Please upload the front and the back of your ID card.");
  }
  /** Resolves a registration by its token (D-175). Unknown, malformed and
   *  expired tokens all answer the same 404 — the token is a random
   *  128-bit value, so there is nothing to enumerate, and the response
   *  must not distinguish "no such registration" from "expired". */
  @Transactional(readOnly = true)
  public VerificationRequest resolveRegistration(String token){
    if (token == null || token.isBlank()) throw new Exceptions.NotFound("Registration");
    var req = requests.findTopByVerificationTokenHashOrderBySubmittedAtDesc(Hashing.sha256(token)).orElse(null);
    if (req == null || req.getVerificationTokenExpiresAt() == null || req.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now()))
      throw new Exceptions.NotFound("Registration");
    return req;
  }
  /** Token-scoped verification status (D-175): no user id, email or name
   *  in the request or the response. */
  @Transactional(readOnly = true)
  public RegistrationStatusDto registrationStatus(String token){
    var req = resolveRegistration(token);
    User u = getUserOr404(req.getUserId());
    return new RegistrationStatusDto(req.getOcrStatus(), req.getStatus(), u.getState(), req.getOcrMatchScore(),
      req.getReason(), otp.hasActiveEmailOtp(u.getId()), u.getEmailVerifiedAt() != null);
  }
  /** Email OTP resend (D-174): same caps as the phone resend (60 s
   *  cooldown, 5/hour per user, 5/hour per email, 10/hour per IP,
   *  atomically acquired). An already-verified email gets the same
   *  generic 200 with no send and no quota consumed — the response
   *  leaks neither existence nor verification state. A delivery failure
   *  is a clean 503, not a silent success. */
  @Transactional
  public Map<String,String> resendEmailOtp(String token, String ip){
    var req = resolveRegistration(token);
    User u = users.findById(req.getUserId()).orElse(null);
    if (u == null || u.getEmailVerifiedAt() != null) return Map.of("status", "Verification code resent");
    // D-174: a code is only ever sent after OCR auto-verified the card.
    // A resend for a failed, inconclusive or still-running verification
    // is refused — the caller already holds the token, so this 409 leaks
    // nothing about any other account.
    var latest = requests.findTopByUserIdOrderBySubmittedAtDesc(u.getId()).orElse(null);
    if (latest == null || !"AUTO_VERIFIED".equals(latest.getOcrStatus()))
      throw new Exceptions.Conflict("VERIFICATION_NOT_COMPLETE", "Your student ID verification has not completed yet.");
    String userKey = "email-otp-resend:" + u.getId();
    String emailKey = u.getEmail() == null || u.getEmail().isBlank() ? null : "email-otp-resend-email:" + u.getEmail().toLowerCase();
    String ipKey = "email-otp-resend-ip:" + ip;
    if (throttle.isLocked(userKey) || (emailKey != null && throttle.isLocked(emailKey)) || throttle.isLocked(ipKey))
      throw new Exceptions.TooManyRequests("Too many verification code requests. Try again later.");
    if (throttle.isCoolingDown(userKey, OTP_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Please wait a minute before requesting another verification code.");
    Map<String,Integer> caps = new LinkedHashMap<>();
    caps.put(userKey, 5);
    if (emailKey != null) caps.put(emailKey, 5);
    caps.put(ipKey, 10);
    if (!throttle.tryAcquireAll(caps, OTP_RESEND_WINDOW, OTP_RESEND_WINDOW, userKey, OTP_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Too many verification code requests. Try again later.");
    if (!otp.sendEmailOtp(u))
      throw new Exceptions.ServiceUnavailable("We could not send your verification code. Please try again in a moment.");
    return Map.of("status", "Verification code resent");
  }
  /** Email OTP verification (D-174/D-176): the code proves control of the
   *  personal inbox. When OCR auto-verified the card, the account is
   *  activated now — REGISTERED → PENDING_REVIEW → VERIFIED in one
   *  transaction (the two legal steps; the state machine is unchanged) —
   *  and the latest request is marked APPROVED so it leaves the admin
   *  queue and cannot be decided twice. The same StudentVerified event
   *  fires as for an admin approval, so the in-app notice and the
   *  decision email behave identically. A MANUAL_REVIEW account stays
   *  PENDING_REVIEW for the admin decision; a REJECTED account stays
   *  rejected. No session is created and the user is not logged in — the
   *  frontend sends the student to /login explicitly. */
  @Transactional
  public EmailOtpVerifyResponse verifyEmailOtp(String token, String code){
    var req = resolveRegistration(token);
    User u = getUserOr404(req.getUserId());
    otp.verifyEmailOtp(u, code);
    users.save(u);
    var latest = requests.findTopByUserIdOrderBySubmittedAtDesc(u.getId()).orElse(null);
    if (latest != null && "AUTO_VERIFIED".equals(latest.getOcrStatus())
        && ("REGISTERED".equals(u.getState()) || "PENDING_REVIEW".equals(u.getState()))){
      AccountState cur = AccountState.of(u.getState());
      if (cur == AccountState.REGISTERED){
        u.setState(cur.transitionTo(AccountState.PENDING_REVIEW).name());
        cur = AccountState.PENDING_REVIEW;
      }
      u.setState(cur.transitionTo(AccountState.VERIFIED).name());
      users.save(u);
      latest.setStatus("APPROVED"); latest.setDecidedAt(LocalDateTime.now());
      requests.save(latest);
      events.publishEvent(new DomainEvents.StudentVerified(u.getId()));
    }
    return new EmailOtpVerifyResponse(u.getEmailVerifiedAt() != null, u.getState());
  }
  @Transactional(readOnly=true)
  public VerificationStatusDto verificationStatus(Long userId){
    User u = getUserOr404(userId);
    var req = requests.findTopByUserIdOrderBySubmittedAtDesc(userId).orElse(null);
    return new VerificationStatusDto(userId, u.getState(), req==null?null:req.getStatus(),
      req==null?null:req.getOcrMatchScore(), req==null?null:req.getOcrStatus(), req==null?null:req.getReason());
  }
}
