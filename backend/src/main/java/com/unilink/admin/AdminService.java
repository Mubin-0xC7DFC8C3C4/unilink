package com.unilink.admin;
import com.unilink.admin.AdminDtos.*;
import com.unilink.audit.AuditService; import com.unilink.common.Exceptions;
import com.unilink.community.FeedService;
import com.unilink.identity.domain.*; import com.unilink.identity.dto.CatalogDtos.*;
import com.unilink.identity.repo.*;
import com.unilink.identity.service.SessionService;
import com.unilink.moderation.ModerationDtos.*;
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
  private final BatchRepository batches; private final SectorTagRepository sectors;
  private final AppealRepository appeals; private final SuspensionRecommendationRepository recommendations;
  private final AdminAlertRepository alerts; private final JdbcTemplate jdbc; private final AuditService audit;
  private final AccessPolicy policy; private final PasswordEncoder encoder; private final SessionService sessions;
  private final UserCredentialRepository creds; private final VerificationAttemptRepository attempts;
  private final AdminTotpSecretRepository totpSecrets;
  private final EventDetailRepository eventDetails; private final FeedService feed;
  public AdminService(UserRepository users, VerificationRequestRepository requests, ReportRepository reports,
      PostRepository posts, CommentRepository comments, AdminAccountRepository admins, AuditLogRepository auditLogs,
      AnnouncementRepository announcements, UniversityRepository universities, DepartmentRepository departments,
      DepartmentCatalogRepository catalogs, BatchRepository batches, SectorTagRepository sectors,
      AppealRepository appeals, SuspensionRecommendationRepository recommendations, AdminAlertRepository alerts,
      JdbcTemplate jdbc, AuditService audit, AccessPolicy policy, PasswordEncoder encoder, SessionService sessions,
      UserCredentialRepository creds, VerificationAttemptRepository attempts,
      AdminTotpSecretRepository totpSecrets,
      EventDetailRepository eventDetails, FeedService feed){
    this.users=users; this.requests=requests; this.reports=reports; this.posts=posts; this.comments=comments;
    this.admins=admins; this.auditLogs=auditLogs; this.announcements=announcements; this.universities=universities;
    this.departments=departments; this.catalogs=catalogs; this.batches=batches; this.sectors=sectors;
    this.appeals=appeals; this.recommendations=recommendations; this.alerts=alerts; this.jdbc=jdbc; this.audit=audit;
    this.policy=policy; this.encoder=encoder; this.sessions=sessions; this.creds=creds; this.attempts=attempts;
    this.totpSecrets=totpSecrets;
    this.eventDetails=eventDetails; this.feed=feed;
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
    m.put("pendingAppeals", count("SELECT COUNT(*) FROM appeals WHERE status='PENDING'"));
    m.put("newUsersToday", count("SELECT COUNT(*) FROM users WHERE DATE(created_at)=CURDATE()" + (uni==null?"":" AND university_id="+uni)));
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
      return VerificationRequestDto.from(r, u, uni, attempts.countByUserId(r.getUserId()));
    }).toList();
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
      case "SUSPEND" -> { u.setState(com.unilink.identity.domain.AccountState.of(from).transitionTo(com.unilink.identity.domain.AccountState.SUSPENDED).name()); }
      case "BAN" -> { u.setState(com.unilink.identity.domain.AccountState.of(from).transitionTo(com.unilink.identity.domain.AccountState.BANNED).name()); }
      case "REACTIVATE" -> { u.setState(com.unilink.identity.domain.AccountState.of(from).transitionTo(com.unilink.identity.domain.AccountState.VERIFIED).name()); }
      case "REVOKE" -> { u.setState(com.unilink.identity.domain.AccountState.of(from).transitionTo(com.unilink.identity.domain.AccountState.REGISTERED).name()); }
      case "FORCE_RESET" -> {
        UserCredential c = creds.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
        temporaryPassword = UUID.randomUUID().toString().substring(0,12)+"A1";
        c.setPasswordHash(encoder.encode(temporaryPassword)); creds.save(c);
      }
      default -> throw new Exceptions.BadRequest("INVALID_ACTION","Unknown user action");
    }
    users.save(u);
    sessions.invalidateAllSessions(u.getEmail());
    audit.write(admin.adminId(), "USER_"+action, "USER", userId, reason, Map.of("from", from), ip);
    return new UserActionResult(AdminUserDto.from(u), temporaryPassword);
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
  @Transactional public UniversityDto createUniversity(Principals.AdminPrincipal admin, String name, String shortName, String domains, String ip){
    policy.requirePlatformAdmin(admin);
    University u = new University(); u.setName(name); u.setShortName(shortName); u.setEmailDomains(domains); u.setActive(true);
    u = universities.save(u); audit.write(admin.adminId(),"MASTERDATA_CREATE","UNIVERSITY",u.getId(),null,Map.of(),ip); return UniversityDto.from(u);
  }
  @Transactional public DepartmentDto createDepartment(Principals.AdminPrincipal admin, Long universityId, Long catalogId, String name, String ip){
    policy.requirePlatformAdmin(admin);
    Department d = new Department(); d.setUniversityId(universityId); d.setCatalogId(catalogId); d.setName(name); d.setActive(true);
    d = departments.save(d); audit.write(admin.adminId(),"MASTERDATA_CREATE","DEPARTMENT",d.getId(),null,Map.of(),ip); return DepartmentDto.from(d);
  }
  @Transactional public SectorTagDto createSector(Principals.AdminPrincipal admin, String name, String ip){
    policy.requirePlatformAdmin(admin);
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
  @Transactional(readOnly=true)
  public Map<String,Object> analytics(Principals.AdminPrincipal admin){
    boolean platform = "PLATFORM_ADMIN".equals(admin.role());
    Long uni = platform ? null : admin.universityId();
    String u = uni==null?"":" AND university_id="+uni;
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("usersByState", jdbc.queryForList("SELECT state, COUNT(*) c FROM users WHERE 1=1"+u+" GROUP BY state"));
    m.put("usersPerDay30", jdbc.queryForList("SELECT DATE(created_at) d, COUNT(*) c FROM users WHERE created_at>=DATE_SUB(CURDATE(),INTERVAL 30 DAY)"+u+" GROUP BY DATE(created_at) ORDER BY d"));
    m.put("eventsPerSector", jdbc.queryForList("SELECT st.name sector, COUNT(*) c FROM post_sector_tags pst JOIN sector_tags st ON st.id=pst.sector_tag_id JOIN posts p ON p.id=pst.post_id WHERE p.type='EVENT' GROUP BY st.name"));
    m.put("verificationTurnaroundHours", jdbc.queryForObject("SELECT AVG(TIMESTAMPDIFF(HOUR, submitted_at, decided_at)) FROM verification_requests WHERE decided_at IS NOT NULL", Double.class));
    m.put("activeUniversities", jdbc.queryForObject("SELECT COUNT(DISTINCT university_id) FROM users WHERE state='VERIFIED'", Long.class));
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
   *  stored or logged in plaintext. The temporary password stays valid until
   *  the admin changes it voluntarily — there is no must-change-on-next-login
   *  enforcement (no admin password-change endpoint exists). The stale TOTP
   *  secret row is deleted so the old secret does not linger; totpEnrolled=false
   *  forces re-enrolment with a fresh secret. */
  @Transactional
  public ResetCredentialsResult resetModeratorCredentials(Principals.AdminPrincipal admin, Long id, String ip){
    policy.requirePlatformAdmin(admin);
    AdminAccount a = admins.findById(id).orElseThrow(() -> new Exceptions.NotFound("Admin"));
    String tmp = UUID.randomUUID().toString().substring(0,12)+"A1";
    a.setPasswordHash(encoder.encode(tmp)); a.setTotpEnrolled(false); admins.save(a);
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
    return DepartmentDto.from(d);
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
}
