package com.unilink.admin;
import com.unilink.admin.AdminDtos.*;
import com.unilink.audit.AuditService; import com.unilink.common.Exceptions;
import com.unilink.community.FeedService;
import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*; import com.unilink.identity.dto.CatalogDtos.*;
import com.unilink.identity.repo.*;
import com.unilink.identity.service.SessionService;
import com.unilink.integration.email.EmailProvider;
import com.unilink.moderation.ModerationDtos.*;
import com.unilink.moderation.ModerationService;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.util.*;
@Service
public class AdminService {
  private final UserRepository users; private final VerificationRequestRepository requests;
  private final ReportRepository reports; private final PostRepository posts; private final CommentRepository comments;
  private final AdminAccountRepository admins; private final AuditLogRepository auditLogs;
  private final AnnouncementRepository announcements; private final UniversityRepository universities;
  private final DepartmentRepository departments; private final DepartmentCatalogRepository catalogs;
  private final DepartmentAliasRepository aliases;
  private final BatchRepository batches; private final SectorTagRepository sectors;
  private final AppealRepository appeals; private final SuspensionRecommendationRepository recommendations;
  private final AdminAlertRepository alerts; private final JdbcTemplate jdbc; private final AuditService audit;
  private final AccessPolicy policy; private final PasswordEncoder encoder; private final SessionService sessions;
  private final UserCredentialRepository creds; private final VerificationAttemptRepository attempts;
  private final AdminTotpSecretRepository totpSecrets;
  private final EventDetailRepository eventDetails; private final FeedService feed;
  private final ModerationService moderation; private final EmailProvider email; private final AppProperties props;
  private final IdentityChangeRequestRepository changeRequests;
  private final PostSectorTagRepository postSectors; private final EventResponseRepository eventResponses;
  private final com.unilink.notification.NotificationService notifications;
  public AdminService(UserRepository users, VerificationRequestRepository requests, ReportRepository reports,
      PostRepository posts, CommentRepository comments, AdminAccountRepository admins, AuditLogRepository auditLogs,
      AnnouncementRepository announcements, UniversityRepository universities, DepartmentRepository departments,
      DepartmentCatalogRepository catalogs, DepartmentAliasRepository aliases, BatchRepository batches, SectorTagRepository sectors,
      AppealRepository appeals, SuspensionRecommendationRepository recommendations, AdminAlertRepository alerts,
      JdbcTemplate jdbc, AuditService audit, AccessPolicy policy, PasswordEncoder encoder, SessionService sessions,
      UserCredentialRepository creds, VerificationAttemptRepository attempts,
      AdminTotpSecretRepository totpSecrets,
      EventDetailRepository eventDetails, FeedService feed,
      ModerationService moderation, EmailProvider email, AppProperties props,
      IdentityChangeRequestRepository changeRequests, PostSectorTagRepository postSectors,
      EventResponseRepository eventResponses, com.unilink.notification.NotificationService notifications){
    this.postSectors=postSectors; this.eventResponses=eventResponses; this.notifications=notifications;
    this.users=users; this.requests=requests; this.reports=reports; this.posts=posts; this.comments=comments;
    this.admins=admins; this.auditLogs=auditLogs; this.announcements=announcements; this.universities=universities;
    this.departments=departments; this.catalogs=catalogs; this.aliases=aliases; this.batches=batches; this.sectors=sectors;
    this.appeals=appeals; this.recommendations=recommendations; this.alerts=alerts; this.jdbc=jdbc; this.audit=audit;
    this.policy=policy; this.encoder=encoder; this.sessions=sessions; this.creds=creds; this.attempts=attempts;
    this.totpSecrets=totpSecrets;
    this.eventDetails=eventDetails; this.feed=feed;
    this.moderation=moderation; this.email=email; this.props=props; this.changeRequests=changeRequests;
  }
  /** Dashboard: real SQL counts only (FR-ADM-02). Moderators see their university only. */
  @Transactional(readOnly=true)
  public Map<String,Object> dashboard(Principals.AdminPrincipal admin){
    boolean platform = "PLATFORM_ADMIN".equals(admin.role());
    Long uni = platform ? null : admin.universityId();
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("totalUsers", count("SELECT COUNT(*) FROM users WHERE state='VERIFIED'" + (uni==null?"":" AND university_id="+uni)));
    m.put("pendingVerifications", count("SELECT COUNT(*) FROM verification_requests vr JOIN users u ON u.id=vr.user_id WHERE vr.status='PENDING_REVIEW'" + (uni==null?"":" AND u.university_id="+uni)));
    m.put("reportsAwaitingReview", count("SELECT COUNT(*) FROM reports r JOIN users u ON u.id=r.reporter_id WHERE r.status='ACTIVE'" + (uni==null?"":" AND u.university_id="+uni)));
    m.put("activeEvents", count("SELECT COUNT(*) FROM posts p JOIN event_details e ON e.post_id=p.id WHERE p.type='EVENT' AND p.status='ACTIVE' AND e.deadline>NOW()" + (uni==null?"":" AND p.scope_university_id="+uni)));
    // I-14: pendingAppeals was global for every moderator; it now joins the
    // appellant so moderators see only their own university's appeals
    // (platform admins keep the global count).
    m.put("pendingAppeals", platform
      ? count("SELECT COUNT(*) FROM appeals WHERE status='PENDING'")
      : count("SELECT COUNT(*) FROM appeals a JOIN users u ON u.id=a.appellant_id WHERE a.status='PENDING' AND u.university_id="+uni));
    m.put("newUsersToday", count("SELECT COUNT(*) FROM users WHERE DATE(created_at)=CURDATE()" + (uni==null?"":" AND university_id="+uni)));
    m.put("totalStudents", count("SELECT COUNT(*) FROM users" + (uni==null?"":" WHERE university_id="+uni)));
    m.put("suspendedStudents", count("SELECT COUNT(*) FROM users WHERE state='SUSPENDED'" + (uni==null?"":" AND university_id="+uni)));
    m.put("totalPosts", count("SELECT COUNT(*) FROM posts WHERE type='POST' AND status='ACTIVE'" + (uni==null?"":" AND scope_university_id="+uni)));
    m.put("publishedEvents", count("SELECT COUNT(*) FROM posts p JOIN event_details e ON e.post_id=p.id WHERE p.type='EVENT' AND e.event_status='PUBLISHED' AND p.status='ACTIVE'" + (uni==null?"":" AND p.scope_university_id="+uni)));
    m.put("upcomingEvents", count("SELECT COUNT(*) FROM posts p JOIN event_details e ON e.post_id=p.id WHERE p.type='EVENT' AND e.event_status='PUBLISHED' AND p.status='ACTIVE' AND e.deadline>NOW()" + (uni==null?"":" AND p.scope_university_id="+uni)));
    return m;
  }
  private long count(String sql){ Long v = jdbc.queryForObject(sql, Long.class); return v==null?0:v; }

  @Transactional(readOnly=true)
  public List<VerificationRequestDto> verificationQueue(Principals.AdminPrincipal admin, String status){
    List<VerificationRequest> all;
    if (status==null || status.isBlank()) all = requests.findAllByOrderBySubmittedAtAsc();
    else if ("PENDING_REVIEW".equals(status) || "APPROVED".equals(status) || "REJECTED".equals(status))
      all = requests.findByStatusOrderBySubmittedAtAsc(status);
    else throw new Exceptions.BadRequest("INVALID_STATUS", "Unknown verification status: " + status);
    if (!"PLATFORM_ADMIN".equals(admin.role())){
      Set<Long> mine = new HashSet<>();
      users.findByUniversityId(admin.universityId()).forEach(u -> mine.add(u.getId()));
      all = all.stream().filter(r -> mine.contains(r.getUserId())).toList();
    }
    Map<Long,User> byId = new HashMap<>();
    users.findAllById(all.stream().map(VerificationRequest::getUserId).distinct().toList())
      .forEach(u -> byId.put(u.getId(), u));
    Map<Long,String> uniNames = new HashMap<>();
    universities.findAllById(byId.values().stream().map(User::getUniversityId)
        .filter(Objects::nonNull).distinct().toList())
      .forEach(u -> uniNames.put(u.getId(), u.getName()));
    return all.stream().map(r -> {
      User u = byId.get(r.getUserId());
      String uni = (u==null || u.getUniversityId()==null) ? null : uniNames.get(u.getUniversityId());
      // I-18: previousAttempts counts attempts on THIS request, not all
      // attempts the user ever made. I-17: flag duplicate phones (D-077).
      boolean duplicatePhone = u!=null && u.getPhone()!=null
        && users.findByPhone(u.getPhone()).stream().anyMatch(o -> !o.getId().equals(u.getId()));
      return VerificationRequestDto.from(r, u, uni, attempts.countByVerificationRequestId(r.getId()), duplicatePhone);
    }).toList();
  }
  // ---- identity-change requests (FR-PROF-09, D-017): admin decide path ----
  /** Pending identity-change requests, scoped like the verification queue:
   *  moderators see only requests from users of their own university. */
  @Transactional(readOnly=true)
  public List<IdentityChangeRequestDto> listIdentityChanges(Principals.AdminPrincipal admin){
    List<IdentityChangeRequest> all = changeRequests.findAll(
      org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "createdAt"));
    if ("PLATFORM_ADMIN".equals(admin.role()))
      return all.stream().map(r -> IdentityChangeRequestDto.from(r, users.findById(r.getUserId()).orElse(null))).toList();
    return all.stream()
      .filter(r -> users.findById(r.getUserId()).map(u -> admin.universityId().equals(u.getUniversityId())).orElse(false))
      .map(r -> IdentityChangeRequestDto.from(r, users.findById(r.getUserId()).orElse(null))).toList();
  }
  /** I-18: decide an identity-change request. On approval the user's
   *  university/department/student ID swap atomically (D-017: the user kept
   *  the old values until now); old posts keep their original scope (D-021).
   *  The new student ID must be free at the new university. */
  @Transactional
  public IdentityChangeRequestDto decideIdentityChange(Principals.AdminPrincipal admin, Long requestId,
                                                       boolean accept, String reason, String ip){
    if (reason==null || reason.isBlank()) throw new Exceptions.BadRequest("REASON_REQUIRED","A reason is mandatory");
    IdentityChangeRequest r = changeRequests.findById(requestId).orElseThrow(() -> new Exceptions.NotFound("Identity change request"));
    User u = users.findById(r.getUserId()).orElseThrow(() -> new Exceptions.NotFound("User"));
    policy.requireModeratorScopeOr404(admin, u.getUniversityId());
    if (!"PENDING".equals(r.getStatus())) throw new Exceptions.Conflict("ALREADY_DECIDED","Request already decided");
    if (accept){
      University uni = universities.findById(r.getNewUniversityId()).orElseThrow(() -> new Exceptions.NotFound("University"));
      if (!Boolean.TRUE.equals(uni.getActive())) throw new Exceptions.BadRequest("UNIVERSITY_INACTIVE","University is not active");
      Department dept = departments.findById(r.getNewDepartmentId()).orElseThrow(() -> new Exceptions.NotFound("Department"));
      if (!dept.getUniversityId().equals(uni.getId())) throw new Exceptions.BadRequest("DEPARTMENT_MISMATCH","Department does not belong to the university");
      if (users.existsByUniversityIdAndStudentId(uni.getId(), r.getNewStudentId())
          && !(uni.getId().equals(u.getUniversityId()) && r.getNewStudentId().equals(u.getStudentId())))
        throw new Exceptions.Conflict("STUDENT_ID_TAKEN","This student ID is already registered at the new university");
      u.setUniversityId(uni.getId()); u.setDepartmentId(dept.getId()); u.setStudentId(r.getNewStudentId());
      if (r.getNewIdCardKey()!=null) u.setIdCardKey(r.getNewIdCardKey());
      users.save(u);
      r.setStatus("APPROVED");
    } else {
      r.setStatus("REJECTED");
    }
    r.setReason(reason); r.setDecidedAt(LocalDateTime.now()); r.setDecidedBy(admin.adminId());
    changeRequests.save(r);
    audit.write(admin.adminId(), "IDENTITY_CHANGE_"+r.getStatus(), "USER", u.getId(), reason, Map.of("requestId", r.getId()), ip);
    return IdentityChangeRequestDto.from(r, u);
  }
  @Transactional(readOnly=true)
  public List<AdminUserDto> listUsers(Principals.AdminPrincipal admin, String q){
    List<User> all = (q==null||q.isBlank()) ? users.findAll() :
      users.findAll().stream().filter(u -> u.getFullName().toLowerCase().contains(q.toLowerCase()) || u.getEmail().toLowerCase().contains(q.toLowerCase())).toList();
    if ("PLATFORM_ADMIN".equals(admin.role())) return all.stream().map(AdminUserDto::from).toList();
    return all.stream().filter(u -> admin.universityId().equals(u.getUniversityId())).map(AdminUserDto::from).toList();
  }
  @Transactional(readOnly=true)
  public AdminUserDto getUser(Principals.AdminPrincipal admin, Long userId){
    return AdminUserDto.from(loadUser(admin, userId));
  }
  /** Entity loader for internal use (scope-checked); the API boundary returns AdminUserDto. */
  private User loadUser(Principals.AdminPrincipal admin, Long userId){
    User u = users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    policy.requireModeratorScopeOr404(admin, u.getUniversityId());
    return u;
  }
  /** ID image key for the authorised stream: loads the request and enforces moderator scope (404 out of scope). */
  @Transactional(readOnly=true)
  public String verificationIdCardKey(Principals.AdminPrincipal admin, Long requestId){
    VerificationRequest r = requests.findById(requestId).orElseThrow(() -> new Exceptions.NotFound("Verification request"));
    loadUser(admin, r.getUserId());
    return r.getIdCardKey();
  }
  /** ID card BACK image key for a verification request (2026-11-22);
   *  null when the student did not upload one. Same scope rules. */
  public String verificationIdCardBackKey(Principals.AdminPrincipal admin, Long requestId){
    VerificationRequest r = requests.findById(requestId).orElseThrow(() -> new Exceptions.NotFound("Verification request"));
    User u = users.findById(r.getUserId()).orElseThrow(() -> new Exceptions.NotFound("User"));
    policy.requireModeratorScopeOr404(admin, u.getUniversityId());
    return u.getIdCardBackKey();
  }
  /** Suspend/ban/reactivate/revoke/force-reset — reason mandatory, sessions revoked (FR-ADM-04).
   *  FORCE_RESET returns the generated temporary password ONCE in the result
   *  (mirroring resetModeratorCredentials); it is never logged or stored in
   *  plaintext. All other actions return a null temporary password. */
  public record UserActionResult(AdminUserDto user, String temporaryPassword) {}
  @Transactional
  public UserActionResult changeUserState(Principals.AdminPrincipal admin, Long userId, String action, String reason, String ip){
    if (reason==null || reason.isBlank()) throw new Exceptions.BadRequest("REASON_REQUIRED","A reason is mandatory");
    User u = loadUser(admin, userId);
    String from = u.getState();
    String temporaryPassword = null;
    switch (action){
      // I-14 (FR-MOD-05, D-036): SUSPEND/BAN are Platform Admin only.
      // Moderators recommend suspension (recommendSuspension) instead.
      case "SUSPEND" -> { policy.requirePlatformAdmin(admin); u.setState(com.unilink.identity.domain.AccountState.of(from).transitionTo(com.unilink.identity.domain.AccountState.SUSPENDED).name()); }
      case "BAN" -> { policy.requirePlatformAdmin(admin); u.setState(com.unilink.identity.domain.AccountState.of(from).transitionTo(com.unilink.identity.domain.AccountState.BANNED).name()); }
      case "REACTIVATE" -> { u.setState(com.unilink.identity.domain.AccountState.of(from).transitionTo(com.unilink.identity.domain.AccountState.VERIFIED).name()); }
      case "REVOKE" -> { u.setState(com.unilink.identity.domain.AccountState.of(from).transitionTo(com.unilink.identity.domain.AccountState.REGISTERED).name()); }
      case "FORCE_RESET" -> {
        UserCredential c = creds.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
        temporaryPassword = UUID.randomUUID().toString().substring(0,12)+"A1";
        c.setPasswordHash(encoder.encode(temporaryPassword));
        // D-094/D-095: the temporary password must be changed at next login
        // and expires 24 h after issue; an expired temp password rejects
        // login (AuthService) and must be re-issued.
        c.setMustChangePassword(true);
        c.setTempPasswordExpiresAt(LocalDateTime.now().plus(com.unilink.identity.service.AuthService.TEMP_PASSWORD_VALIDITY));
        creds.save(c);
      }
      default -> throw new Exceptions.BadRequest("INVALID_ACTION","Unknown user action");
    }
    users.save(u);
    sessions.invalidateAllSessions(u.getEmail());
    // I-13 (D-019): a suspension or ban issues the single-use appeal link
    // and emails it — the POST /api/appeals/public path was dead until now
    // because no token was ever created. Email failure must not roll back
    // the suspension itself.
    if ("SUSPEND".equals(action) || "BAN".equals(action)) issueSuspensionAppeal(u, reason);
    audit.write(admin.adminId(), "USER_"+action, "USER", userId, reason, Map.of("from", from), ip);
    return new UserActionResult(AdminUserDto.from(u), temporaryPassword);
  }
  /** I-13: create the single-use appeal token (ModerationService) and email
   *  the link to the user. The token is returned once here and never stored
   *  in plaintext; only its hash lives on the appeal row. */
  private void issueSuspensionAppeal(User u, String reason){
    try {
      var token = moderation.createSuspensionAppealToken(u.getId());
      String base = props.baseUrl()==null ? "" : props.baseUrl();
      String link = base + "/appeals/public?token=" + token.token();
      email.sendSimple(u.getEmail(), "Your UniLink account was suspended",
        "Your UniLink account was suspended: "+reason
        + ". You can appeal within 7 days using this link (single use): " + link);
    } catch (Exception ex){
      // The suspension stands; the appeal link can be re-issued by a
      // Platform Admin. Log loudly — a missing appeal email is a D-019
      // failure, not a silent one.
      org.slf4j.LoggerFactory.getLogger(AdminService.class)
        .error("Failed to issue/email suspension appeal link for user {}", u.getId(), ex);
    }
  }
  /** Moderator recommends suspension; only a Platform Admin confirms it (D-036). */
  @Transactional
  public SuspensionRecommendationDto recommendSuspension(Principals.AdminPrincipal mod, Long userId, String reason, String ip){
    if ("PLATFORM_ADMIN".equals(mod.role())) throw new Exceptions.BadRequest("INVALID_ROLE","Platform Admins suspend directly");
    User u = loadUser(mod, userId);
    if (reason==null || reason.isBlank()) throw new Exceptions.BadRequest("REASON_REQUIRED","A reason is mandatory");
    SuspensionRecommendation r = new SuspensionRecommendation();
    r.setUserId(userId); r.setModeratorId(mod.adminId()); r.setReason(reason); r.setStatus("PENDING");
    r = recommendations.save(r);
    audit.write(mod.adminId(), "SUSPENSION_RECOMMEND", "USER", userId, reason, Map.of("recommendationId", r.getId()), ip);
    return SuspensionRecommendationDto.from(r);
  }
  @Transactional
  public SuspensionRecommendationDto decideRecommendation(Principals.AdminPrincipal admin, Long id, boolean confirm, String ip){
    policy.requirePlatformAdmin(admin);
    SuspensionRecommendation r = recommendations.findById(id).orElseThrow(() -> new Exceptions.NotFound("Recommendation"));
    if (!"PENDING".equals(r.getStatus())) throw new Exceptions.Conflict("ALREADY_DECIDED","Recommendation already decided");
    r.setStatus(confirm?"CONFIRMED":"DECLINED"); r.setDecidedBy(admin.adminId()); r.setDecidedAt(LocalDateTime.now());
    recommendations.save(r);
    if (confirm){
      User u = users.findById(r.getUserId()).orElseThrow(() -> new Exceptions.NotFound("User"));
      u.setState(com.unilink.identity.domain.AccountState.of(u.getState()).transitionTo(com.unilink.identity.domain.AccountState.SUSPENDED).name());
      users.save(u); sessions.invalidateAllSessions(u.getEmail());
      issueSuspensionAppeal(u, r.getReason());
    }
    audit.write(admin.adminId(), "SUSPENSION_RECOMMEND_"+r.getStatus(), "USER", r.getUserId(), r.getReason(), Map.of("recommendationId", r.getId()), ip);
    return SuspensionRecommendationDto.from(r);
  }
  /** Suspension recommendations visible to the caller (ARCH: repository reads live in the service). */
  @Transactional(readOnly=true)
  public List<SuspensionRecommendationDto> listRecommendations(Principals.AdminPrincipal admin){
    if ("PLATFORM_ADMIN".equals(admin.role())) return recommendations.findByStatusOrderByCreatedAtAsc("PENDING").stream().map(SuspensionRecommendationDto::from).toList();
    return recommendations.findByModeratorIdOrderByCreatedAtDesc(admin.adminId()).stream().map(SuspensionRecommendationDto::from).toList();
  }
  /** Pending appeals, scoped to the caller's university (moderators see only
   *  appeals from users of their own university; out-of-scope appeals are not
   *  listed, so their existence is not leaked). */
  @Transactional(readOnly=true)
  public List<AppealDto> pendingAppeals(Principals.AdminPrincipal admin){
    List<Appeal> all = appeals.findByStatusOrderByCreatedAtAsc("PENDING");
    if ("PLATFORM_ADMIN".equals(admin.role())) return all.stream().map(AppealDto::from).toList();
    return all.stream().filter(a -> admin.universityId()!=null && admin.universityId().equals(appealUniversityId(a)))
      .map(AppealDto::from).toList();
  }
  /** The university an appeal belongs to, via its appellant. Unknown appellant
   *  (deleted user) maps to null, i.e. out of scope for every moderator. */
  private Long appealUniversityId(Appeal a){
    return users.findById(a.getAppellantId()).map(User::getUniversityId).orElse(null);
  }
  /** Overdue-verification alerts for the calling admin. */
  @Transactional(readOnly=true)
  public List<AdminAlertDto> adminAlerts(Principals.AdminPrincipal admin){
    return alerts.findByAdminIdOrderByCreatedAtDesc(admin.adminId()).stream().map(AdminAlertDto::from).toList();
  }
  // ---- moderation decisions (ModerationFacade behaviour) ----
  @Transactional
  public void decideContent(Principals.AdminPrincipal admin, String targetType, Long targetId, String action, String reason, String ip){
    if (reason==null || reason.isBlank()) throw new Exceptions.BadRequest("REASON_REQUIRED","A reason is mandatory");
    // D-140: a null targetType/targetId/action used to NPE the switches
    // below (500). All three are client errors.
    if (targetType==null || targetId==null) throw new Exceptions.BadRequest("INVALID_TARGET","Unknown moderation target");
    if (action==null) throw new Exceptions.BadRequest("INVALID_ACTION","Unknown action");
    switch (targetType){
      case "POST", "EVENT" -> {
        Post p = posts.findById(targetId).orElseThrow(() -> new Exceptions.NotFound("Post"));
        policy.requireModeratorScopeOr404(admin, p.getScopeUniversityId());
        p.setStatus(switch (action){ case "HIDE" -> "HIDDEN_ADMIN"; case "REMOVE" -> "REMOVED";
          case "RESTORE" -> "ACTIVE"; case "EDIT_REQUEST" -> "EDIT_REQUESTED"; default -> throw new Exceptions.BadRequest("INVALID_ACTION","Unknown action"); });
        posts.save(p);
      }
      case "COMMENT" -> {
        Comment c = comments.findById(targetId).orElseThrow(() -> new Exceptions.NotFound("Comment"));
        Post p = posts.findById(c.getPostId()).orElseThrow(() -> new Exceptions.NotFound("Post"));
        policy.requireModeratorScopeOr404(admin, p.getScopeUniversityId());
        c.setStatus(switch (action){ case "HIDE" -> "HIDDEN_ADMIN"; case "REMOVE" -> "REMOVED";
          case "RESTORE" -> "ACTIVE"; case "EDIT_REQUEST" -> "EDIT_REQUESTED"; default -> throw new Exceptions.BadRequest("INVALID_ACTION","Unknown action"); });
        comments.save(c);
      }
      default -> throw new Exceptions.BadRequest("INVALID_TARGET","Unknown target");
    }
    audit.write(admin.adminId(), "CONTENT_"+action, targetType, targetId, reason, Map.of(), ip);
  }
  @Transactional
  public AppealDto decideAppeal(Principals.AdminPrincipal admin, Long appealId, boolean accept, String reason, String ip){
    if (reason==null || reason.isBlank()) throw new Exceptions.BadRequest("REASON_REQUIRED","A reason is mandatory");
    Appeal a = appeals.findById(appealId).orElseThrow(() -> new Exceptions.NotFound("Appeal"));
    policy.requireModeratorScopeOr404(admin, appealUniversityId(a));
    if (!"PENDING".equals(a.getStatus())) throw new Exceptions.Conflict("ALREADY_DECIDED","Appeal already decided");
    a.setStatus(accept?"ACCEPTED":"REJECTED"); a.setDecidedBy(admin.adminId()); a.setDecisionReason(reason);
    a.setDecidedAt(LocalDateTime.now()); appeals.save(a);
    if (accept && ("POST".equals(a.getTargetType()) || "EVENT".equals(a.getTargetType()))){
      posts.findById(a.getTargetId()).ifPresent(p -> { if (!"REMOVED".equals(p.getStatus())){ p.setStatus("ACTIVE"); posts.save(p); } });
    }
    audit.write(admin.adminId(), "APPEAL_"+(accept?"ACCEPT":"REJECT"), "APPEAL", appealId, reason, Map.of(), ip);
    return AppealDto.from(a);
  }
  @Transactional(readOnly=true)
  public List<ReportDto> moderationQueue(Principals.AdminPrincipal admin){
    // Ordered by report count then age (FR-ADM-05): group by target, count desc, oldest first.
    List<Report> all = reports.findByStatusOrderByCreatedAtAsc("ACTIVE");
    Map<String,Long> counts = new HashMap<>();
    for (Report r : all) counts.merge(r.getTargetType()+":"+r.getTargetId(), 1L, Long::sum);
    all.sort(Comparator.comparingLong((Report r) -> counts.get(r.getTargetType()+":"+r.getTargetId())).reversed()
      .thenComparing(Report::getCreatedAt));
    if ("PLATFORM_ADMIN".equals(admin.role())) return all.stream().map(ReportDto::from).toList();
    return all.stream().filter(r -> {
      Long uni = switch (r.getTargetType()){
        case "POST","EVENT" -> posts.findById(r.getTargetId()).map(Post::getScopeUniversityId).orElse(null);
        case "COMMENT" -> comments.findById(r.getTargetId()).map(c -> posts.findById(c.getPostId()).map(Post::getScopeUniversityId).orElse(null)).orElse(null);
        case "USER" -> users.findById(r.getTargetId()).map(User::getUniversityId).orElse(null);
        default -> null;
      };
      return admin.universityId().equals(uni);
    }).map(ReportDto::from).toList();
  }
  // ---- announcements (D-039) ----
  @Transactional
  public AnnouncementDto createAnnouncement(Principals.AdminPrincipal admin, String title, String body, String scope, Long universityId, LocalDateTime expiresAt, String ip){
    if (scope==null || (!"GLOBAL".equals(scope) && !"UNIVERSITY".equals(scope)))
      throw new Exceptions.BadRequest("INVALID_SCOPE","Announcement scope must be GLOBAL or UNIVERSITY");
    if ("GLOBAL".equals(scope)) policy.requirePlatformAdmin(admin);
    else policy.requireModeratorScopeOr404(admin, universityId);
    Announcement a = new Announcement(); a.setAdminId(admin.adminId()); a.setTitle(title); a.setBody(body);
    a.setScope(scope); a.setUniversityId("GLOBAL".equals(scope)?null:universityId); a.setExpiresAt(expiresAt); a.setActive(true);
    a = announcements.save(a);
    audit.write(admin.adminId(), "ANNOUNCEMENT_CREATE", "ANNOUNCEMENT", a.getId(), null, Map.of("scope", scope), ip);
    return AnnouncementDto.from(a);
  }
  @Transactional(readOnly=true)
  public List<AnnouncementDto> activeAnnouncements(Principals.UnilinkUserPrincipal p){
    return announcements.findByActiveTrueOrderByCreatedAtDesc().stream()
      .filter(a -> (a.getExpiresAt()==null || a.getExpiresAt().isAfter(LocalDateTime.now()))
        && ("GLOBAL".equals(a.getScope()) || (p.universityId()!=null && p.universityId().equals(a.getUniversityId()))))
      .map(AnnouncementDto::from).toList();
  }
  // ---- master data (FR-ADM-10): deactivate, never hard-delete referenced rows ----
  /** D-192: category is "PUBLIC" or "PRIVATE" (validated); null keeps it
   *  unclassified. The admin API is how future universities are added
   *  without a frontend change. */
  @Transactional public UniversityDto createUniversity(Principals.AdminPrincipal admin, String name, String shortName, String domains, String category, String ip){
    policy.requirePlatformAdmin(admin);
    if (category != null && !"PUBLIC".equals(category) && !"PRIVATE".equals(category))
      throw new Exceptions.BadRequest("INVALID_CATEGORY","University category must be PUBLIC or PRIVATE");
    // D-149: universities.name has a unique key (V001 uk_universities_name)
    // and there is no DataIntegrityViolation handler, so a duplicate used
    // to surface as a 500 INTERNAL_ERROR instead of a 409.
    if (universities.findByName(name).isPresent()) throw new Exceptions.Conflict("UNIVERSITY_EXISTS","A university with this name already exists");
    University u = new University(); u.setName(name); u.setShortName(shortName); u.setEmailDomains(domains); u.setCategory(category); u.setActive(true);
    u = universities.save(u); audit.write(admin.adminId(),"MASTERDATA_CREATE","UNIVERSITY",u.getId(),null,Map.of(),ip); return UniversityDto.from(u);
  }
  @Transactional public DepartmentDto createDepartment(Principals.AdminPrincipal admin, Long universityId, Long catalogId, String name, String ip){
    policy.requirePlatformAdmin(admin);
    // D-149: the department used to be saved blindly — a bogus university
    // or catalog id failed on the V001 FK as a 500, and a duplicate
    // (university, catalog) pair failed on uk_dept_uni_catalog as a 500.
    if (universityId==null || universities.findById(universityId).isEmpty())
      throw new Exceptions.NotFound("University");
    if (catalogId==null || catalogs.findById(catalogId).isEmpty())
      throw new Exceptions.NotFound("Department catalog");
    if (departments.findByUniversityIdAndCatalogId(universityId, catalogId).isPresent())
      throw new Exceptions.Conflict("DEPARTMENT_EXISTS","This university already has a department for that catalog entry");
    Department d = new Department(); d.setUniversityId(universityId); d.setCatalogId(catalogId); d.setName(name); d.setActive(true);
    d = departments.save(d); audit.write(admin.adminId(),"MASTERDATA_CREATE","DEPARTMENT",d.getId(),null,Map.of(),ip); return enrichedDepartment(d);
  }
  /** D-198: create a canonical department-catalog row (+ optional aliases)
   *  so the catalogue can grow without a migration. Platform Admin only.
   *  Duplicate name or duplicate alias (globally unique) is a 409, not a
   *  500 from the unique key (D-149 pattern). */
  @Transactional public DepartmentCatalogDto createDepartmentCatalog(Principals.AdminPrincipal admin, String name, String shortName, List<String> aliasList, String ip){
    policy.requirePlatformAdmin(admin);
    if (name==null || name.isBlank()) throw new Exceptions.BadRequest("NAME_REQUIRED","A department catalog entry needs a name");
    if (catalogs.findByName(name.trim()).isPresent())
      throw new Exceptions.Conflict("CATALOG_EXISTS","A department catalog entry with this name already exists");
    List<String> cleanAliases = cleanAliases(aliasList);
    for (String a : cleanAliases) {
      if (aliases.existsByAliasIgnoreCase(a))
        throw new Exceptions.Conflict("ALIAS_EXISTS","Alias '"+a+"' is already used by another department");
    }
    DepartmentCatalog c = new DepartmentCatalog(); c.setName(name.trim()); c.setShortName(shortName); c.setActive(true);
    c = catalogs.save(c);
    for (String a : cleanAliases) { DepartmentAlias row = new DepartmentAlias(); row.setCatalogId(c.getId()); row.setAlias(a); row.setActive(true); aliases.save(row); }
    audit.write(admin.adminId(),"MASTERDATA_CREATE","DEPARTMENT_CATALOG",c.getId(),null,Map.of(),ip);
    return DepartmentCatalogDto.from(c, cleanAliases);
  }
  /** D-198: add one alias to an existing catalog row. Alias is globally
   *  unique: a duplicate (case-insensitive) is a 409. */
  @Transactional public DepartmentCatalogDto addCatalogAlias(Principals.AdminPrincipal admin, Long catalogId, String alias, String ip){
    policy.requirePlatformAdmin(admin);
    DepartmentCatalog c = catalogs.findById(catalogId).orElseThrow(() -> new Exceptions.NotFound("Department catalog"));
    if (alias==null || alias.isBlank()) throw new Exceptions.BadRequest("ALIAS_REQUIRED","An alias is required");
    if (aliases.existsByAliasIgnoreCase(alias.trim()))
      throw new Exceptions.Conflict("ALIAS_EXISTS","Alias '"+alias.trim()+"' is already used by another department");
    DepartmentAlias row = new DepartmentAlias(); row.setCatalogId(c.getId()); row.setAlias(alias.trim()); row.setActive(true); aliases.save(row);
    audit.write(admin.adminId(),"MASTERDATA_CREATE","DEPARTMENT_ALIAS",row.getId(),null,Map.of("catalogId", c.getId()),ip);
    return enrichedCatalog(c);
  }
  @Transactional public DepartmentCatalogDto updateDepartmentCatalog(Principals.AdminPrincipal admin, Long id, String name, String shortName, Boolean active, String ip){
    policy.requirePlatformAdmin(admin);
    if (name!=null && name.isBlank()) throw new Exceptions.BadRequest("NAME_REQUIRED","A department catalog entry needs a name");
    DepartmentCatalog c = catalogs.findById(id).orElseThrow(() -> new Exceptions.NotFound("Department catalog"));
    if (name!=null && !name.equals(c.getName()) && catalogs.findByName(name).isPresent())
      throw new Exceptions.Conflict("CATALOG_EXISTS","A department catalog entry with this name already exists");
    if (name!=null) c.setName(name);
    if (shortName!=null) c.setShortName(shortName);
    if (active!=null) c.setActive(active);
    catalogs.save(c);
    audit.write(admin.adminId(),"MASTERDATA_UPDATE","DEPARTMENT_CATALOG",id,null,Map.of(),ip);
    return enrichedCatalog(c);
  }
  private DepartmentCatalogDto enrichedCatalog(DepartmentCatalog c){
    List<String> names = aliases.findByCatalogIdAndActiveTrue(c.getId()).stream().map(DepartmentAlias::getAlias).toList();
    return DepartmentCatalogDto.from(c, names);
  }
  private DepartmentDto enrichedDepartment(Department d){
    String shortName = d.getCatalogId()==null ? null
        : catalogs.findById(d.getCatalogId()).map(DepartmentCatalog::getShortName).orElse(null);
    List<String> names = d.getCatalogId()==null ? List.of()
        : aliases.findByCatalogIdAndActiveTrue(d.getCatalogId()).stream().map(DepartmentAlias::getAlias).toList();
    return DepartmentDto.from(d, shortName, names);
  }
  private static List<String> cleanAliases(List<String> aliasList){
    if (aliasList==null) return List.of();
    return aliasList.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
  }
  @Transactional public SectorTagDto createSector(Principals.AdminPrincipal admin, String name, String ip){
    policy.requirePlatformAdmin(admin);
    // D-149: sector_tags.name has a unique key (V001 uk_sector_name); same
    // 500-instead-of-409 problem as createUniversity above.
    if (sectors.findByName(name).isPresent()) throw new Exceptions.Conflict("SECTOR_EXISTS","A sector with this name already exists");
    SectorTag s = new SectorTag(); s.setName(name); s.setActive(true);
    s = sectors.save(s); audit.write(admin.adminId(),"MASTERDATA_CREATE","SECTOR",s.getId(),null,Map.of(),ip); return SectorTagDto.from(s);
  }
  // ---- moderator management (FR-ADM-12) ----
  @Transactional
  public AdminAccountDto createModerator(Principals.AdminPrincipal admin, String email, Long universityId, String password, String ip){
    policy.requirePlatformAdmin(admin);
    if (password==null || password.length()<12) throw new Exceptions.BadRequest("WEAK_PASSWORD","Admin passwords must be at least 12 characters (D-031)");
    if (admins.findByEmail(email).isPresent()) throw new Exceptions.Conflict("EMAIL_TAKEN","Admin email already exists");
    AdminAccount a = new AdminAccount(); a.setEmail(email); a.setPasswordHash(encoder.encode(password));
    a.setRole("UNIVERSITY_MODERATOR"); a.setUniversityId(universityId); a.setActive(true); a.setTotpEnrolled(false);
    // D-120: the initial password is Platform-Admin-issued, so the
    // moderator must rotate it at first login — same rule as the bootstrap
    // admin (D-119) and resetModeratorCredentials (D-094). No 24 h expiry
    // is set here: unlike a reset, a new moderator may legitimately first
    // log in days after creation, and an expiry would lock them out
    // before first use (owner to decide if that should change).
    a.setMustChangePassword(true);
    a = admins.save(a);
    audit.write(admin.adminId(), "MODERATOR_CREATE", "ADMIN", a.getId(), null, Map.of("universityId", universityId), ip);
    return AdminAccountDto.from(a);
  }
  @Transactional
  public void deactivateAdmin(Principals.AdminPrincipal admin, Long adminId, String reason, String ip){
    policy.requirePlatformAdmin(admin);
    AdminAccount a = admins.findById(adminId).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    a.setActive(false); admins.save(a);
    sessions.invalidateAllSessions(a.getEmail());
    audit.write(admin.adminId(), "ADMIN_DEACTIVATE", "ADMIN", adminId, reason, Map.of(), ip);
  }
  // ---- analytics: SQL aggregates only (FR-ADM-13) ----
  // I-14: the three global aggregates are scoped to the moderator's
  // university. usersByState/usersPerDay30 were already scoped; the other
  // three were visible to any moderator in full.
  @Transactional(readOnly=true)
  public Map<String,Object> analytics(Principals.AdminPrincipal admin){
    boolean platform = "PLATFORM_ADMIN".equals(admin.role());
    Long uni = platform ? null : admin.universityId();
    String u = uni==null?"":" AND university_id="+uni;
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("usersByState", jdbc.queryForList("SELECT state, COUNT(*) c FROM users WHERE 1=1"+u+" GROUP BY state"));
    m.put("usersPerDay30", jdbc.queryForList("SELECT DATE(created_at) d, COUNT(*) c FROM users WHERE created_at>=DATE_SUB(CURDATE(),INTERVAL 30 DAY)"+u+" GROUP BY DATE(created_at) ORDER BY d"));
    m.put("eventsPerSector", jdbc.queryForList("SELECT st.name sector, COUNT(*) c FROM post_sector_tags pst JOIN sector_tags st ON st.id=pst.sector_tag_id JOIN posts p ON p.id=pst.post_id WHERE p.type='EVENT'" + (uni==null?"":" AND p.scope_university_id="+uni) + " GROUP BY st.name"));
    m.put("verificationTurnaroundHours", jdbc.queryForObject("SELECT AVG(TIMESTAMPDIFF(HOUR, submitted_at, decided_at)) FROM verification_requests vr" + (uni==null?"":" JOIN users u ON u.id=vr.user_id AND u.university_id="+uni) + " WHERE decided_at IS NOT NULL", Double.class));
    m.put("activeUniversities", jdbc.queryForObject("SELECT COUNT(DISTINCT university_id) FROM users WHERE state='VERIFIED'" + (uni==null?"":" AND university_id="+uni), Long.class));
    return m;
  }
  @Transactional(readOnly=true)
  public List<AuditLogDto> auditLogs(Principals.AdminPrincipal admin, int limit){
    if (limit<0) throw new Exceptions.BadRequest("INVALID_LIMIT","limit must be >= 0");
    // Moderators see only their university's actions: audit rows carry no university, so moderators see
    // only rows whose target is a user of their university or their own actions. (Documented limitation.)
    List<AuditLog> all = auditLogs.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"id"));
    if ("PLATFORM_ADMIN".equals(admin.role())) return all.stream().limit(limit).map(AuditLogDto::from).toList();
    return all.stream().filter(l -> admin.adminId().equals(l.getActorAdminId())
      || ("USER".equals(l.getTargetType()) && l.getTargetId()!=null && users.findById(l.getTargetId()).map(x->admin.universityId().equals(x.getUniversityId())).orElse(false)))
      .limit(limit).map(AuditLogDto::from).toList();
  }
  /** Overdue alerts job helper (D-038): one alert per overdue request, idempotent. */
  @Transactional
  public int createOverdueAlerts(){
    int created = 0;
    int hours = 48;
    LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
    for (VerificationRequest r : requests.findByStatusOrderBySubmittedAtAsc("PENDING_REVIEW")){
      if (r.getSubmittedAt()!=null && r.getSubmittedAt().isAfter(cutoff)) continue;
      User u = users.findById(r.getUserId()).orElse(null); if (u==null) continue;
      List<AdminAccount> mods = admins.findByRoleAndUniversityId("UNIVERSITY_MODERATOR", u.getUniversityId());
      List<AdminAccount> targets = mods.isEmpty() ? admins.findByRole("PLATFORM_ADMIN") : mods;
      for (AdminAccount a : targets){
        if (alerts.existsByTypeAndTargetTypeAndTargetIdAndAdminId("VERIFICATION_OVERDUE","VERIFICATION_REQUEST",r.getId(),a.getId())) continue;
        AdminAlert al = new AdminAlert(); al.setAdminId(a.getId()); al.setUniversityId(u.getUniversityId());
        al.setType("VERIFICATION_OVERDUE"); al.setTargetType("VERIFICATION_REQUEST"); al.setTargetId(r.getId());
        al.setMessage("Verification request #"+r.getId()+" is overdue (>48h)"); alerts.save(al); created++;
      }
    }
    return created;
  }
  // ---- moderators: list / edit / reset credentials (FR-ADM-12) ----
  @Transactional(readOnly=true)
  public List<AdminAccountDto> listModerators(Principals.AdminPrincipal admin){
    if ("PLATFORM_ADMIN".equals(admin.role()))
      return admins.findAll().stream().sorted(Comparator.comparing(AdminAccount::getEmail)).map(AdminAccountDto::from).toList();
    return admins.findAll().stream()
      .filter(a -> admin.universityId()!=null && admin.universityId().equals(a.getUniversityId()))
      .sorted(Comparator.comparing(AdminAccount::getEmail)).map(AdminAccountDto::from).toList();
  }
  @Transactional
  public AdminAccountDto updateModerator(Principals.AdminPrincipal admin, Long id, String email, Long universityId, Boolean active){
    policy.requirePlatformAdmin(admin);
    AdminAccount a = admins.findById(id).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    if (email!=null && (email.isBlank() || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")))
      throw new Exceptions.BadRequest("INVALID_EMAIL","Admin email must be a valid email address");
    if (admin.adminId().equals(id) && Boolean.FALSE.equals(active))
      throw new Exceptions.BadRequest("SELF_DEACTIVATE","You cannot deactivate your own account");
    if (admin.adminId().equals(id) && email!=null && !email.isBlank() && !email.equals(a.getEmail()))
      throw new Exceptions.BadRequest("SELF_EMAIL_CHANGE","You cannot change your own email address");
    if (email!=null && !email.isBlank() && !email.equals(a.getEmail())){
      if (admins.findByEmail(email).isPresent()) throw new Exceptions.Conflict("EMAIL_TAKEN","Admin email already exists");
      a.setEmail(email);
    }
    if (universityId!=null) a.setUniversityId(universityId);
    if (active!=null) a.setActive(active);
    admins.save(a);
    return AdminAccountDto.from(a);
  }
  public record ResetCredentialsResult(AdminAccountDto admin, String temporaryPassword) {}
  /** Reset an admin's credentials (FR-ADM-12): the temporary password is
   *  returned ONCE in this response (like the TOTP recovery codes) and never
   *  stored or logged in plaintext. D-094/D-095: the reset sets
   *  must_change_password and a 24 h temporary-password expiry, so the next
   *  login is restricted until the admin changes the password (POST
   *  /api/admin/auth/change-password, after MFA) and an expired temporary
   *  password rejects login until a Platform Admin re-issues it. The stale
   *  TOTP secret row is deleted so the old secret does not linger;
   *  totpEnrolled=false forces re-enrolment with a fresh secret. */
  @Transactional
  public ResetCredentialsResult resetModeratorCredentials(Principals.AdminPrincipal admin, Long id, String ip){
    policy.requirePlatformAdmin(admin);
    AdminAccount a = admins.findById(id).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    String tmp = UUID.randomUUID().toString().substring(0,12)+"A1";
    a.setPasswordHash(encoder.encode(tmp)); a.setTotpEnrolled(false);
    a.setMustChangePassword(true);
    a.setTempPasswordExpiresAt(LocalDateTime.now().plus(com.unilink.identity.service.AuthService.TEMP_PASSWORD_VALIDITY));
    admins.save(a);
    if (totpSecrets.existsById(id)) totpSecrets.deleteById(id);
    sessions.invalidateAllSessions(a.getEmail());
    audit.write(admin.adminId(), "ADMIN_CREDENTIALS_RESET", "ADMIN", id, null, Map.of(), ip);
    return new ResetCredentialsResult(AdminAccountDto.from(a), tmp);
  }
  // ---- master data: updates + deactivate + batches (FR-ADM-10) ----
  @Transactional
  public UniversityDto updateUniversity(Principals.AdminPrincipal admin, Long id, String name, String shortName, String emailDomains, Boolean active, String ip){
    policy.requirePlatformAdmin(admin);
    if (name!=null && name.isBlank()) throw new Exceptions.BadRequest("NAME_REQUIRED","A university needs a name");
    University u = universities.findById(id).orElseThrow(() -> new Exceptions.NotFound("University"));
    if (name!=null) u.setName(name);
    if (shortName!=null) u.setShortName(shortName);
    if (emailDomains!=null) u.setEmailDomains(emailDomains);
    if (active!=null) u.setActive(active);
    universities.save(u);
    audit.write(admin.adminId(),"MASTERDATA_UPDATE","UNIVERSITY",id,null,Map.of(),ip);
    return UniversityDto.from(u);
  }
  @Transactional
  public DepartmentDto updateDepartment(Principals.AdminPrincipal admin, Long id, String name, Boolean active, String ip){
    policy.requirePlatformAdmin(admin);
    if (name!=null && name.isBlank()) throw new Exceptions.BadRequest("NAME_REQUIRED","A department needs a name");
    Department d = departments.findById(id).orElseThrow(() -> new Exceptions.NotFound("Department"));
    if (name!=null) d.setName(name);
    if (active!=null) d.setActive(active);
    departments.save(d);
    audit.write(admin.adminId(),"MASTERDATA_UPDATE","DEPARTMENT",id,null,Map.of(),ip);
    return enrichedDepartment(d);
  }
  @Transactional
  public SectorTagDto updateSector(Principals.AdminPrincipal admin, Long id, String name, Boolean active, String ip){
    policy.requirePlatformAdmin(admin);
    if (name!=null && name.isBlank()) throw new Exceptions.BadRequest("NAME_REQUIRED","A sector needs a name");
    SectorTag s = sectors.findById(id).orElseThrow(() -> new Exceptions.NotFound("Sector"));
    if (name!=null) s.setName(name);
    if (active!=null) s.setActive(active);
    sectors.save(s);
    audit.write(admin.adminId(),"MASTERDATA_UPDATE","SECTOR",id,null,Map.of(),ip);
    return SectorTagDto.from(s);
  }
  @Transactional
  public BatchDto createBatch(Principals.AdminPrincipal admin, Long universityId, Long departmentId, String label, Integer startYear, String ip){
    policy.requirePlatformAdmin(admin);
    if (label==null || label.isBlank()) throw new Exceptions.BadRequest("LABEL_REQUIRED","A batch needs a label");
    if (universityId==null || universities.findById(universityId).isEmpty())
      throw new Exceptions.NotFound("University");
    if (departmentId==null || departments.findById(departmentId).isEmpty())
      throw new Exceptions.NotFound("Department");
    boolean duplicate = batches.findByUniversityIdAndDepartmentIdAndActiveTrue(universityId, departmentId).stream()
      .anyMatch(b -> label.equals(b.getLabel()));
    if (duplicate) throw new Exceptions.Conflict("BATCH_EXISTS","A batch with this label already exists for the university and department");
    Batch b = new Batch(); b.setUniversityId(universityId); b.setDepartmentId(departmentId);
    b.setLabel(label); b.setStartYear(startYear); b.setActive(true);
    b = batches.save(b);
    audit.write(admin.adminId(),"MASTERDATA_CREATE","BATCH",b.getId(),null,Map.of(),ip);
    return BatchDto.from(b);
  }
  @Transactional
  public BatchDto updateBatch(Principals.AdminPrincipal admin, Long id, String label, Integer startYear, Boolean active, String ip){
    policy.requirePlatformAdmin(admin);
    if (label!=null && label.isBlank()) throw new Exceptions.BadRequest("LABEL_REQUIRED","A batch needs a label");
    Batch b = batches.findById(id).orElseThrow(() -> new Exceptions.NotFound("Batch"));
    if (label!=null) b.setLabel(label);
    if (startYear!=null) b.setStartYear(startYear);
    if (active!=null) b.setActive(active);
    batches.save(b);
    audit.write(admin.adminId(),"MASTERDATA_UPDATE","BATCH",id,null,Map.of(),ip);
    return BatchDto.from(b);
  }
  /** Deactivate, never hard-delete, referenced rows (FR-ADM-10). */
  @Transactional
  public void deactivateMasterData(Principals.AdminPrincipal admin, String kind, Long id, String ip){
    policy.requirePlatformAdmin(admin);
    switch (kind){
      case "universities" -> { University u = universities.findById(id).orElseThrow(() -> new Exceptions.NotFound("University")); u.setActive(false); universities.save(u); }
      case "departments" -> { Department d = departments.findById(id).orElseThrow(() -> new Exceptions.NotFound("Department")); d.setActive(false); departments.save(d); }
      case "department-catalog" -> { DepartmentCatalog c = catalogs.findById(id).orElseThrow(() -> new Exceptions.NotFound("Department catalog")); c.setActive(false); catalogs.save(c); }
      case "sectors" -> { SectorTag s = sectors.findById(id).orElseThrow(() -> new Exceptions.NotFound("Sector")); s.setActive(false); sectors.save(s); }
      case "batches" -> { Batch b = batches.findById(id).orElseThrow(() -> new Exceptions.NotFound("Batch")); b.setActive(false); batches.save(b); }
      default -> throw new Exceptions.BadRequest("INVALID_KIND","Unknown master-data kind");
    }
    audit.write(admin.adminId(),"MASTERDATA_DEACTIVATE",kind.toUpperCase(),id,null,Map.of(),ip);
  }
  // ---- announcements: list / update / delete (FR-ADM-09) ----
  @Transactional(readOnly=true)
  public List<AnnouncementDto> listAnnouncements(Principals.AdminPrincipal admin){
    List<Announcement> all = announcements.findAll(org.springframework.data.domain.Sort.by(
      org.springframework.data.domain.Sort.Direction.DESC,"createdAt"));
    if ("PLATFORM_ADMIN".equals(admin.role())) return all.stream().map(AnnouncementDto::from).toList();
    return all.stream()
      .filter(a -> "GLOBAL".equals(a.getScope()) || (a.getUniversityId()!=null && a.getUniversityId().equals(admin.universityId())))
      .map(AnnouncementDto::from).toList();
  }
  @Transactional
  public AnnouncementDto updateAnnouncement(Principals.AdminPrincipal admin, Long id, String title, String body,
                                            LocalDateTime expiresAt, Boolean active, String ip){
    Announcement a = announcements.findById(id).orElseThrow(() -> new Exceptions.NotFound("Announcement"));
    if (!"PLATFORM_ADMIN".equals(admin.role())) policy.requireModeratorScopeOr404(admin, a.getUniversityId());
    if (title!=null) a.setTitle(title);
    if (body!=null) a.setBody(body);
    if (expiresAt!=null) a.setExpiresAt(expiresAt);
    if (active!=null) a.setActive(active);
    announcements.save(a);
    audit.write(admin.adminId(),"ANNOUNCEMENT_UPDATE","ANNOUNCEMENT",id,null,Map.of(),ip);
    return AnnouncementDto.from(a);
  }
  /** Announcements are deactivated, never hard-deleted. */
  @Transactional
  public void deleteAnnouncement(Principals.AdminPrincipal admin, Long id, String ip){
    Announcement a = announcements.findById(id).orElseThrow(() -> new Exceptions.NotFound("Announcement"));
    if (!"PLATFORM_ADMIN".equals(admin.role())) policy.requireModeratorScopeOr404(admin, a.getUniversityId());
    a.setActive(false); announcements.save(a);
    audit.write(admin.adminId(),"ANNOUNCEMENT_DELETE","ANNOUNCEMENT",id,null,Map.of(),ip);
  }
  // ---- event moderation (FR-ADM-07) ----
  @Transactional(readOnly=true)
  public List<FeedService.PostDto> listEventsForModeration(Principals.AdminPrincipal admin){
    List<Post> all = posts.findAll(org.springframework.data.domain.Sort.by(
      org.springframework.data.domain.Sort.Direction.DESC,"createdAt")).stream()
      .filter(p -> "EVENT".equals(p.getType())).toList();
    if ("PLATFORM_ADMIN".equals(admin.role())) return all.stream().map(feed::toDto).toList();
    return all.stream().filter(p -> admin.universityId()!=null && admin.universityId().equals(p.getScopeUniversityId()))
      .map(feed::toDto).toList();
  }
  /** Remove a fake event, close registration, or fix its deadline/visibility. */
  @Transactional
  public FeedService.PostDto moderateEvent(Principals.AdminPrincipal admin, Long postId, String status,
                                           LocalDateTime deadline, String visibility, Boolean registrationClosed, String ip){
    Post p = posts.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (!"EVENT".equals(p.getType())) throw new Exceptions.BadRequest("NOT_AN_EVENT","Post is not an event");
    policy.requireModeratorScopeOr404(admin, p.getScopeUniversityId());
    if (status!=null){
      p.setStatus(switch (status){
        case "REMOVE" -> "REMOVED"; case "RESTORE" -> "ACTIVE"; case "HIDE" -> "HIDDEN_ADMIN";
        default -> throw new Exceptions.BadRequest("INVALID_ACTION","Unknown event action");
      });
    }
    if (visibility!=null){
      if (!List.of("DEPARTMENT","UNIVERSITY","DEPARTMENT_HUB","NATIONAL").contains(visibility))
        throw new Exceptions.BadRequest("INVALID_VISIBILITY","Unknown visibility");
      p.setVisibility(visibility);
    }
    posts.save(p);
    if (deadline!=null || registrationClosed!=null){
      EventDetail ev = eventDetails.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Event"));
      if (deadline!=null) ev.setDeadline(deadline);
      if (registrationClosed!=null) ev.setRegistrationClosed(registrationClosed);
      eventDetails.save(ev);
    }
    audit.write(admin.adminId(),"EVENT_MODERATE","EVENT",postId,null,Map.of(),ip);
    return feed.toDto(p);
  }

  // ---- Admin event management (2026-11-22): events are admin-created only.
  // The post row carries no student author; created_by_admin_id records the
  // acting admin and the lifecycle lives on event_details.event_status.
  public record AdminEventInput(String title, String body, String shortDescription, String visibility,
      LocalDateTime deadline, LocalDateTime endAt, String timezone, String location, String venueName,
      String fullAddress, String meetingLink, String eventType, String organizerName, String organizingUnit,
      String contactInfo, Boolean registrationRequired, LocalDateTime registrationDeadline, String regLink,
      Integer maxParticipants, List<Long> sectorIds, Long universityId, Long departmentId, String status) {}
  public record RegistrationDto(Long userId, String fullName, String email, String response, LocalDateTime createdAt) {}

  private EventDetail requireEventDetail(Long postId){
    Post p = posts.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (!"EVENT".equals(p.getType())) throw new Exceptions.BadRequest("NOT_AN_EVENT","Post is not an event");
    return eventDetails.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Event"));
  }
  private Post requireEventPost(Principals.AdminPrincipal admin, Long postId){
    Post p = posts.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (!"EVENT".equals(p.getType())) throw new Exceptions.BadRequest("NOT_AN_EVENT","Post is not an event");
    policy.requireModeratorScopeOr404(admin, p.getScopeUniversityId());
    return p;
  }
  private void applyEventScopes(Post p, AdminEventInput in){
    String vis = in.visibility()==null ? "NATIONAL" : in.visibility();
    if (!List.of("DEPARTMENT","UNIVERSITY","DEPARTMENT_HUB","NATIONAL").contains(vis))
      throw new Exceptions.BadRequest("INVALID_VISIBILITY","Unknown visibility");
    p.setVisibility(vis);
    p.setScopeUniversityId(null); p.setScopeDepartmentId(null); p.setScopeCatalogId(null);
    if ("UNIVERSITY".equals(vis) || "DEPARTMENT".equals(vis) || "DEPARTMENT_HUB".equals(vis)){
      if (in.universityId()==null) throw new Exceptions.BadRequest("UNIVERSITY_REQUIRED","Choose a university for this visibility");
      p.setScopeUniversityId(in.universityId());
    }
    if ("DEPARTMENT".equals(vis) && in.departmentId()!=null) p.setScopeDepartmentId(in.departmentId());
    if ("DEPARTMENT_HUB".equals(vis) && in.departmentId()!=null)
      departments.findById(in.departmentId()).ifPresent(d -> p.setScopeCatalogId(d.getCatalogId()));
  }
  private void applyEventFields(EventDetail ev, AdminEventInput in){
    if (in.deadline()!=null) ev.setDeadline(in.deadline());
    if (in.endAt()!=null) ev.setEndAt(in.endAt());
    if (in.timezone()!=null) ev.setTimezone(in.timezone());
    if (in.location()!=null) ev.setLocation(in.location());
    if (in.venueName()!=null) ev.setVenueName(in.venueName());
    if (in.fullAddress()!=null) ev.setFullAddress(in.fullAddress());
    if (in.meetingLink()!=null) ev.setMeetingLink(in.meetingLink());
    if (in.eventType()!=null) ev.setEventType(in.eventType());
    if (in.organizerName()!=null) ev.setOrganizerName(in.organizerName());
    if (in.organizingUnit()!=null) ev.setOrganizingUnit(in.organizingUnit());
    if (in.contactInfo()!=null) ev.setContactInfo(in.contactInfo());
    if (in.shortDescription()!=null) ev.setShortDescription(in.shortDescription());
    if (in.registrationRequired()!=null) ev.setRegistrationRequired(in.registrationRequired());
    if (in.registrationDeadline()!=null) ev.setRegistrationDeadline(in.registrationDeadline());
    if (in.regLink()!=null) ev.setRegLink(in.regLink());
    if (in.maxParticipants()!=null) ev.setMaxParticipants(in.maxParticipants());
  }
  @Transactional
  public FeedService.PostDto createAdminEvent(Principals.AdminPrincipal admin, AdminEventInput in, String ip){
    if (in.title()==null || in.title().isBlank()) throw new Exceptions.BadRequest("TITLE_REQUIRED","Event title is required");
    if (in.body()==null || in.body().isBlank()) throw new Exceptions.BadRequest("DESCRIPTION_REQUIRED","Event description is required");
    if (in.deadline()==null) throw new Exceptions.BadRequest("DATE_REQUIRED","Event start date and time are required");
    String status = in.status()==null ? "PUBLISHED" : in.status();
    if (!List.of("DRAFT","PUBLISHED").contains(status)) throw new Exceptions.BadRequest("INVALID_STATUS","New events start as DRAFT or PUBLISHED");
    if ("PUBLISHED".equals(status) && !in.deadline().isAfter(LocalDateTime.now()))
      throw new Exceptions.BadRequest("PAST_DEADLINE","Event start must be in the future to publish");
    if (in.registrationDeadline()!=null && in.registrationDeadline().isAfter(in.deadline()))
      throw new Exceptions.BadRequest("INVALID_REGISTRATION_DEADLINE","Registration deadline must be before the event starts");
    Post p = new Post();
    p.setAuthorId(null); p.setType("EVENT"); p.setTitle(in.title()); p.setBody(in.body());
    applyEventScopes(p, in);
    policy.requireModeratorScopeOr404(admin, p.getScopeUniversityId());
    p.setStatus("DRAFT".equals(status) ? "DRAFT" : "ACTIVE");
    p = posts.save(p);
    EventDetail ev = new EventDetail();
    ev.setId(p.getId()); ev.setDeadline(in.deadline()); ev.setRegistrationClosed(false);
    ev.setEventStatus(status); ev.setCreatedByAdminId(admin.adminId());
    ev.setOrganizerName(in.organizerName()); ev.setLocation(in.location()); ev.setRegLink(in.regLink());
    applyEventFields(ev, in);
    eventDetails.save(ev);
    if (in.sectorIds()!=null) for (Long sid : new java.util.LinkedHashSet<>(in.sectorIds())){
      if (!sectors.existsById(sid)) throw new Exceptions.NotFound("Sector");
      PostSectorTag t = new PostSectorTag(); t.setPostId(p.getId()); t.setSectorTagId(sid); postSectors.save(t);
    }
    audit.write(admin.adminId(), "DRAFT".equals(status) ? "EVENT_CREATED_DRAFT" : "EVENT_PUBLISHED", "EVENT", p.getId(), null, Map.of(), ip);
    return feed.toDto(p);
  }
  @Transactional
  public FeedService.PostDto updateAdminEvent(Principals.AdminPrincipal admin, Long id, AdminEventInput in, String ip){
    Post p = requireEventPost(admin, id);
    EventDetail ev = requireEventDetail(id);
    if ("CANCELLED".equals(ev.getEventStatus())) throw new Exceptions.BadRequest("EVENT_CANCELLED","A cancelled event cannot be edited");
    if (in.title()!=null && !in.title().isBlank()) p.setTitle(in.title());
    if (in.body()!=null && !in.body().isBlank()) p.setBody(in.body());
    if (in.visibility()!=null) applyEventScopes(p, in);
    applyEventFields(ev, in);
    posts.save(p); eventDetails.save(ev);
    audit.write(admin.adminId(), "EVENT_UPDATED", "EVENT", id, null, Map.of(), ip);
    return feed.toDto(p);
  }
  @Transactional
  public FeedService.PostDto publishAdminEvent(Principals.AdminPrincipal admin, Long id, String ip){
    Post p = requireEventPost(admin, id);
    EventDetail ev = requireEventDetail(id);
    if (!"DRAFT".equals(ev.getEventStatus())) throw new Exceptions.BadRequest("NOT_A_DRAFT","Only a draft event can be published");
    if (ev.getDeadline()==null || !ev.getDeadline().isAfter(LocalDateTime.now()))
      throw new Exceptions.BadRequest("PAST_DEADLINE","Event start must be in the future to publish");
    ev.setEventStatus("PUBLISHED"); p.setStatus("ACTIVE");
    posts.save(p); eventDetails.save(ev);
    audit.write(admin.adminId(), "EVENT_PUBLISHED", "EVENT", id, null, Map.of(), ip);
    return feed.toDto(p);
  }
  @Transactional
  public FeedService.PostDto cancelAdminEvent(Principals.AdminPrincipal admin, Long id, String ip){
    Post p = requireEventPost(admin, id);
    EventDetail ev = requireEventDetail(id);
    if ("CANCELLED".equals(ev.getEventStatus())) throw new Exceptions.BadRequest("ALREADY_CANCELLED","Event is already cancelled");
    ev.setEventStatus("CANCELLED"); p.setStatus("CANCELLED");
    posts.save(p); eventDetails.save(ev);
    for (EventResponse r : eventResponses.findByPostIdOrderByCreatedAtAsc(id))
      notifications.notify(r.getUserId(), "EVENT_CANCELLED", "EVENT", id, "Event cancelled", (p.getTitle()==null ? "An event" : p.getTitle()) + " has been cancelled");
    audit.write(admin.adminId(), "EVENT_CANCELLED", "EVENT", id, null, Map.of(), ip);
    return feed.toDto(p);
  }
  @Transactional
  public void deleteAdminEvent(Principals.AdminPrincipal admin, Long id, String ip){
    Post p = requireEventPost(admin, id);
    requireEventDetail(id);
    p.setStatus("REMOVED"); posts.save(p);
    audit.write(admin.adminId(), "EVENT_DELETED", "EVENT", id, null, Map.of(), ip);
  }
  @Transactional(readOnly=true)
  public FeedService.PostDto getAdminEvent(Principals.AdminPrincipal admin, Long id){
    Post p = requireEventPost(admin, id);
    requireEventDetail(id);
    return feed.toDto(p);
  }
  @Transactional(readOnly=true)
  public List<RegistrationDto> eventRegistrations(Principals.AdminPrincipal admin, Long id){
    requireEventPost(admin, id);
    requireEventDetail(id);
    return eventResponses.findByPostIdOrderByCreatedAtAsc(id).stream().map(r -> {
      User u = users.findById(r.getUserId()).orElse(null);
      return new RegistrationDto(r.getUserId(), u==null?null:u.getFullName(), u==null?null:u.getEmail(), r.getResponse(), r.getCreatedAt());
    }).toList();
  }
}
