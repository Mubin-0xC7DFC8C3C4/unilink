package com.unilink.identity.service;
import com.unilink.common.Exceptions; import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*; import com.unilink.identity.dto.AuthDtos.*;
import com.unilink.identity.repo.*; import com.unilink.security.LoginThrottle;
import com.unilink.storage.StorageGateway; import com.unilink.storage.UploadValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.*; import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException; import java.time.Duration; import java.time.LocalDateTime; import java.util.Map;
@Service
public class AuthService {
  private final UserRepository users; private final UserCredentialRepository creds;
  private final UniversityRepository universities; private final DepartmentRepository departments;
  private final BatchRepository batches; private final VerificationRequestRepository requests;
  private final VerificationAttemptRepository attempts; private final ProfileRepository profiles;
  private final EmailVerificationTokenRepository emailTokenRepo;
  private final StorageGateway storage; private final UploadValidator uploads; private final OtpService otp;
  private final PasswordEncoder encoder; private final LoginThrottle throttle;
  private final AuthenticationManager authManager; private final SessionService sessions;
  private final OcrJobService ocrJob; private final AppProperties props;
  public AuthService(UserRepository users, UserCredentialRepository creds, UniversityRepository universities,
      DepartmentRepository departments, BatchRepository batches, VerificationRequestRepository requests,
      VerificationAttemptRepository attempts, ProfileRepository profiles, EmailVerificationTokenRepository emailTokenRepo, StorageGateway storage,
      UploadValidator uploads, OtpService otp, PasswordEncoder encoder, LoginThrottle throttle,
      @Qualifier("studentAuthManager") AuthenticationManager authManager, SessionService sessions,
      OcrJobService ocrJob, AppProperties props){
    this.users=users; this.creds=creds; this.universities=universities; this.departments=departments; this.emailTokenRepo=emailTokenRepo;
    this.batches=batches; this.requests=requests; this.attempts=attempts; this.profiles=profiles;
    this.storage=storage; this.uploads=uploads; this.otp=otp; this.encoder=encoder; this.throttle=throttle;
    this.authManager=authManager; this.sessions=sessions; this.ocrJob=ocrJob; this.props=props;
  }
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
    if (form.getIdCard()==null || form.getIdCard().isEmpty()) throw new Exceptions.BadRequest("ID_CARD_REQUIRED","Student ID card image is required (FR-AUTH-01)");
    if (form.getProfilePhoto()==null || form.getProfilePhoto().isEmpty()) throw new Exceptions.BadRequest("PROFILE_PHOTO_REQUIRED","Profile photo is required (FR-AUTH-01)");
    byte[] idBytes = bytes(form.getIdCard()); byte[] photoBytes = bytes(form.getProfilePhoto());
    uploads.validateImage(idBytes, UploadValidator.ID_CARD_MAX, form.getIdCard().getContentType());
    uploads.validateImage(photoBytes, UploadValidator.PHOTO_MAX, form.getProfilePhoto().getContentType());
    String idKey = storage.store("id-cards", form.getIdCard().getOriginalFilename(), "image/jpeg", idBytes);
    String photoKey = storage.store("profile-photos", form.getProfilePhoto().getOriginalFilename(), "image/jpeg", photoBytes);
    User u = new User();
    u.setUniversityId(uni.getId()); u.setDepartmentId(dept.getId()); u.setBatchId(batchId);
    u.setFullName(form.getFullName()); u.setEmail(form.getEmail()); u.setPhone(form.getPhone());
    u.setAddress(form.getAddress()); u.setStudentId(form.getStudentId());
    u.setState(AccountState.REGISTERED.name()); u.setIdCardKey(idKey); u.setProfilePhotoKey(photoKey);
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
    otp.sendEmailVerificationLink(u, props.baseUrl());
    ocrJob.runOcr(req.getId(), idKey, u.getFullName(), u.getStudentId());
    return toDto(u);
  }
  public static UserDto toDto(User u){
    return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getUniversityId(), u.getDepartmentId(),
      u.getBatchId(), u.getStudentId(), u.getState(), u.getPhoneVerifiedAt()!=null, u.getEmailVerifiedAt()!=null, u.getCreatedAt());
  }
  public User getUserOr404(Long id){ return users.findById(id).orElseThrow(() -> new Exceptions.NotFound("User")); }
  @Transactional
  public UserDto verifyPhone(OtpVerifyRequest r){
    User u = getUserOr404(r.userId());
    otp.verifyPhoneOtp(u, r.otp());
    users.save(u); maybeMoveToPendingReview(u); return toDto(u);
  }
  /** OTP resend caps (FR-AUTH-04 / NFR-SEC-05): 60 s cooldown per user,
   *  at most 5 resends per hour per user, at most 10 per hour per IP.
   *  Reuses the LoginThrottle bucket pattern; no new table.
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
    String userKey = "otp-resend:"+userId; String ipKey = "otp-resend-ip:"+ip;
    if (throttle.isLocked(userKey) || throttle.isLocked(ipKey))
      throw new Exceptions.TooManyRequests("Too many OTP requests. Try again later.");
    if (throttle.isCoolingDown(userKey, OTP_RESEND_COOLDOWN))
      throw new Exceptions.TooManyRequests("Please wait a minute before requesting another OTP.");
    if (!throttle.tryAcquire(userKey, 5, OTP_RESEND_WINDOW, OTP_RESEND_WINDOW)
        || !throttle.tryAcquire(ipKey, 10, OTP_RESEND_WINDOW, OTP_RESEND_WINDOW))
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
  /** Email link carries only the token; resolve the user from the newest unused token. */
  @Transactional
  public UserDto verifyEmailByToken(String token){
    var row = emailTokenRepo.findAll().stream()
      .filter(t -> !Boolean.TRUE.equals(t.getUsed()) && com.unilink.common.Hashing.sha256(token).equals(t.getTokenHash()))
      .max(java.util.Comparator.comparing(com.unilink.identity.domain.EmailVerificationToken::getCreatedAt))
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
      throttle.recordSuccess("login:"+email);
      return auth;
    } catch (Exceptions.AppException e){ throw e; }
      catch (org.springframework.security.core.AuthenticationException e){
      throttle.recordFailure("login:"+email);
      throw new Exceptions.BadRequest("INVALID_CREDENTIALS","Email or password is incorrect"); // generic error (FR-AUTH-10)
    }
  }
  @Transactional
  public void requestPasswordReset(String email){
    users.findByEmail(email).ifPresent(u -> { if (!"DELETED".equals(u.getState())) otp.sendPasswordResetOtp(u); });
    // Always succeed silently: never reveal whether the email exists.
  }
  @Transactional
  public void confirmPasswordReset(PasswordResetConfirm r){
    checkPasswordRule(r.newPassword());
    User u = users.findByEmail(r.email()).orElseThrow(() -> new Exceptions.BadRequest("INVALID_CREDENTIALS","Email or code is incorrect"));
    otp.verifyPasswordResetOtp(u, r.otp());
    UserCredential c = creds.findById(u.getId()).orElseThrow(() -> new Exceptions.NotFound("User"));
    c.setPasswordHash(encoder.encode(r.newPassword())); creds.save(c);
    sessions.invalidateAllSessions(u.getEmail());
  }
  @Transactional
  public void changePassword(Long userId, ChangePasswordRequest r){
    checkPasswordRule(r.newPassword());
    User u = getUserOr404(userId);
    UserCredential c = creds.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (!encoder.matches(r.oldPassword(), c.getPasswordHash())) throw new Exceptions.BadRequest("INVALID_CREDENTIALS","Current password is incorrect");
    c.setPasswordHash(encoder.encode(r.newPassword())); creds.save(c);
    sessions.invalidateAllSessions(u.getEmail());
  }
  @Transactional
  public UserDto resubmit(Long userId, MultipartFile idCard){
    User u = getUserOr404(userId);
    if (!"REJECTED".equals(u.getState())) throw new Exceptions.BadRequest("NOT_REJECTED","Only rejected accounts can resubmit");
    LocalDateTime since = LocalDateTime.now().minusHours(24);
    long recent = attempts.countByUserIdAndCreatedAtAfter(userId, since);
    // count resubmissions via verification_attempts outcome=RESUBMIT in 24h (D-012)
    if (recent >= 3) throw new Exceptions.TooManyRequests("Resubmission limit reached (3 per 24 hours). Try again later.");
    String idKey = u.getIdCardKey();
    if (idCard!=null && !idCard.isEmpty()){
      byte[] b = bytes(idCard); uploads.validateImage(b, UploadValidator.ID_CARD_MAX, idCard.getContentType());
      idKey = storage.store("id-cards", idCard.getOriginalFilename(), "image/jpeg", b);
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
    ocrJob.runOcr(req.getId(), idKey, u.getFullName(), u.getStudentId());
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
