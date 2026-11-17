package com.unilink.admin.api;
import com.unilink.admin.AdminService; import com.unilink.admin.AdminDtos.*;
import com.unilink.identity.dto.CatalogDtos.*; import com.unilink.identity.service.VerificationFacade;
import com.unilink.moderation.ModerationDtos.*;
import com.unilink.security.AccessPolicy; import com.unilink.security.IpResolver; import com.unilink.security.Principals;
import com.unilink.storage.StorageGateway;
import com.unilink.storage.UploadValidator;
import jakarta.servlet.http.HttpServletRequest; import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*; import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** Admin Portal API (FR-ADM-01..14, FR-MOD-01..05). All endpoints require MFA_VERIFIED (filter chain).
 *  Thin controller (ARCH): HTTP translation only — all reads/writes go through services and return DTOs. */
@RestController @RequestMapping("/api/admin")
public class AdminController {
  private final AdminService admin; private final VerificationFacade verification;
  private final AccessPolicy policy; private final StorageGateway storage; private final IpResolver ipResolver;
  private final UploadValidator uploads;
  public AdminController(AdminService admin, VerificationFacade verification, AccessPolicy policy, StorageGateway storage, IpResolver ipResolver, UploadValidator uploads){
    this.admin=admin; this.verification=verification; this.policy=policy; this.storage=storage; this.ipResolver=ipResolver; this.uploads=uploads;
  }
  private Principals.AdminPrincipal admin(Authentication a){ return policy.requireAdmin(a.getPrincipal()); }
  /** Audit IP (D-100): X-Forwarded-For is honoured only from a trusted
   *  proxy — the old code trusted it from any client, so a caller could
   *  forge the IP recorded in the audit log. */
  private String ip(HttpServletRequest req){ return ipResolver.resolve(req); }
  public record ReasonBody(String reason) {}
  public record DecideBody(String action, String reason) {}
  public record ContentDecideBody(String targetType, Long targetId, String action, String reason) {}
  public record AnnouncementBody(String title, String body, String scope, Long universityId, java.time.LocalDateTime expiresAt) {}
  public record MasterBody(String name, String shortName, String emailDomains, Long universityId, Long catalogId) {}
  public record ModeratorBody(String email, Long universityId, String password) {}
  @GetMapping("/dashboard") public Map<String,Object> dashboard(Authentication a){ return admin.dashboard(admin(a)); }
  @GetMapping("/verification-queue") public List<VerificationRequestDto> queue(Authentication a, @RequestParam(required=false) String status){ return admin.verificationQueue(admin(a), status); }
  @PostMapping("/verification/{id}/approve") public VerificationRequestDto approve(Authentication a, @PathVariable Long id, HttpServletRequest req){
    return verification.approve(id, admin(a), ip(req));
  }
  @PostMapping("/verification/{id}/reject") public VerificationRequestDto reject(Authentication a, @PathVariable Long id, @RequestBody ReasonBody b, HttpServletRequest req){
    return verification.reject(id, admin(a), b.reason(), ip(req));
  }
  /** ID image: authorised stream only, never a public URL (D-053).
   *  D-106: the content type is sniffed from the stored bytes — the endpoint
   *  used to always answer image/jpeg even for a PNG/WebP ID card. */
  @GetMapping("/verification/{id}/id-image")
  public ResponseEntity<ByteArrayResource> idImage(Authentication a, @PathVariable Long id){
    String key = admin.verificationIdCardKey(admin(a), id); // enforces moderator scope (404 out of scope)
    if (key==null || !storage.exists(key)) throw new com.unilink.common.Exceptions.NotFound("ID image");
    byte[] bytes;
    try { bytes = storage.open(key).readAllBytes(); }
    catch (java.io.IOException e){ throw new IllegalStateException("Storage unavailable", e); }
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(uploads.sniffContentType(bytes, null)))
      .header("Cache-Control","no-store")
      .body(new ByteArrayResource(bytes));
  }
  @GetMapping("/users") public List<AdminUserDto> users(Authentication a, @RequestParam(required=false) String q){ return admin.listUsers(admin(a), q); }
  /** Identity-change requests (FR-PROF-09, D-017): list + decide. */
  @GetMapping("/identity-changes") public List<IdentityChangeRequestDto> identityChanges(Authentication a){
    return admin.listIdentityChanges(admin(a));
  }
  @PostMapping("/identity-changes/{id}/decide") public IdentityChangeRequestDto decideIdentityChange(Authentication a, @PathVariable Long id, @RequestBody DecideBody b, HttpServletRequest req){
    return admin.decideIdentityChange(admin(a), id, "ACCEPT".equalsIgnoreCase(b.action()), b.reason(), ip(req));
  }
  @GetMapping("/users/{id}") public AdminUserDto user(Authentication a, @PathVariable Long id){ return admin.getUser(admin(a), id); }
  @PostMapping("/users/{id}/{action}") public AdminService.UserActionResult userAction(Authentication a, @PathVariable Long id, @PathVariable String action, @RequestBody ReasonBody b, HttpServletRequest req){
    return admin.changeUserState(admin(a), id, action.toUpperCase(Locale.ROOT), b.reason(), ip(req));
  }
  @PostMapping("/users/{id}/recommend-suspension") public SuspensionRecommendationDto recommend(Authentication a, @PathVariable Long id, @RequestBody ReasonBody b, HttpServletRequest req){
    return admin.recommendSuspension(admin(a), id, b.reason(), ip(req));
  }
  @GetMapping("/suspension-recommendations") public List<SuspensionRecommendationDto> recommendations(Authentication a){
    return admin.listRecommendations(admin(a));
  }
  @PostMapping("/suspension-recommendations/{id}/decide") public SuspensionRecommendationDto decideRecommendation(Authentication a, @PathVariable Long id, @RequestBody DecideBody b, HttpServletRequest req){
    return admin.decideRecommendation(admin(a), id, "CONFIRM".equalsIgnoreCase(b.action()), ip(req));
  }
  @GetMapping("/moderation-queue") public List<ReportDto> moderationQueue(Authentication a){ return admin.moderationQueue(admin(a)); }
  @PostMapping("/moderation/decide") public Map<String,String> decideContent(Authentication a, @RequestBody ContentDecideBody b, HttpServletRequest req){
    admin.decideContent(admin(a), b.targetType(), b.targetId(), b.action(), b.reason(), ip(req));
    return Map.of("status","ok");
  }
  @GetMapping("/appeals") public List<AppealDto> appeals(Authentication a){
    return admin.pendingAppeals(admin(a));
  }
  @PostMapping("/appeals/{id}/decide") public AppealDto decideAppeal(Authentication a, @PathVariable Long id, @RequestBody DecideBody b, HttpServletRequest req){
    return admin.decideAppeal(admin(a), id, "ACCEPT".equalsIgnoreCase(b.action()), b.reason(), ip(req));
  }
  @PostMapping("/announcements") public AnnouncementDto announce(Authentication a, @RequestBody AnnouncementBody b, HttpServletRequest req){
    return admin.createAnnouncement(admin(a), b.title(), b.body(), b.scope(), b.universityId(), b.expiresAt(), ip(req));
  }
  @GetMapping("/announcements") public List<AnnouncementDto> announcements(Authentication a){
    return admin.listAnnouncements(admin(a));
  }
  public record AnnouncementUpdate(String title, String body, java.time.LocalDateTime expiresAt, Boolean active) {}
  @PutMapping("/announcements/{id}") public AnnouncementDto updateAnnouncement(Authentication a, @PathVariable Long id, @RequestBody AnnouncementUpdate b, HttpServletRequest req){
    return admin.updateAnnouncement(admin(a), id, b.title(), b.body(), b.expiresAt(), b.active(), ip(req));
  }
  @DeleteMapping("/announcements/{id}") public Map<String,String> deleteAnnouncement(Authentication a, @PathVariable Long id, HttpServletRequest req){
    admin.deleteAnnouncement(admin(a), id, ip(req)); return Map.of("status","deleted");
  }
  /** Event moderation list (FR-ADM-07): events in the caller's scope. */
  @GetMapping("/events") public List<com.unilink.community.FeedService.PostDto> events(Authentication a){
    return admin.listEventsForModeration(admin(a));
  }
  public record EventModerateBody(String status, java.time.LocalDateTime deadline, String visibility, Boolean registrationClosed) {}
  @PatchMapping("/events/{id}") public com.unilink.community.FeedService.PostDto moderateEvent(Authentication a, @PathVariable Long id, @RequestBody EventModerateBody b, HttpServletRequest req){
    return admin.moderateEvent(admin(a), id, b.status(), b.deadline(), b.visibility(), b.registrationClosed(), ip(req));
  }
  @PostMapping("/master-data/universities") public UniversityDto createUniversity(Authentication a, @RequestBody MasterBody b, HttpServletRequest req){
    return admin.createUniversity(admin(a), b.name(), b.shortName(), b.emailDomains(), ip(req));
  }
  public record MasterUpdate(String name, String shortName, String emailDomains, String label, Integer startYear, Boolean active) {}
  @PutMapping("/master-data/universities/{id}") public UniversityDto updateUniversity(Authentication a, @PathVariable Long id, @RequestBody MasterUpdate b, HttpServletRequest req){
    return admin.updateUniversity(admin(a), id, b.name(), b.shortName(), b.emailDomains(), b.active(), ip(req));
  }
  @PostMapping("/master-data/departments") public DepartmentDto createDepartment(Authentication a, @RequestBody MasterBody b, HttpServletRequest req){
    return admin.createDepartment(admin(a), b.universityId(), b.catalogId(), b.name(), ip(req));
  }
  @PutMapping("/master-data/departments/{id}") public DepartmentDto updateDepartment(Authentication a, @PathVariable Long id, @RequestBody MasterUpdate b, HttpServletRequest req){
    return admin.updateDepartment(admin(a), id, b.name(), b.active(), ip(req));
  }
  @PostMapping("/master-data/sectors") public SectorTagDto createSector(Authentication a, @RequestBody MasterBody b, HttpServletRequest req){
    return admin.createSector(admin(a), b.name(), ip(req));
  }
  @PutMapping("/master-data/sectors/{id}") public SectorTagDto updateSector(Authentication a, @PathVariable Long id, @RequestBody MasterUpdate b, HttpServletRequest req){
    return admin.updateSector(admin(a), id, b.name(), b.active(), ip(req));
  }
  public record BatchBody(Long universityId, Long departmentId, String label, Integer startYear) {}
  @PostMapping("/master-data/batches") public BatchDto createBatch(Authentication a, @RequestBody BatchBody b, HttpServletRequest req){
    return admin.createBatch(admin(a), b.universityId(), b.departmentId(), b.label(), b.startYear(), ip(req));
  }
  @PutMapping("/master-data/batches/{id}") public BatchDto updateBatch(Authentication a, @PathVariable Long id, @RequestBody MasterUpdate b, HttpServletRequest req){
    return admin.updateBatch(admin(a), id, b.label(), b.startYear(), b.active(), ip(req));
  }
  /** Deactivate, never hard-delete, a master-data row. */
  @PostMapping("/master-data/{kind}/{id}/deactivate") public Map<String,String> deactivateMasterData(Authentication a, @PathVariable String kind, @PathVariable Long id, HttpServletRequest req){
    admin.deactivateMasterData(admin(a), kind, id, ip(req)); return Map.of("status","deactivated");
  }
  @GetMapping("/moderators") public List<AdminAccountDto> moderators(Authentication a){
    return admin.listModerators(admin(a));
  }
  public record ModeratorUpdate(String email, Long universityId, Boolean active) {}
  @PutMapping("/moderators/{id}") public AdminAccountDto updateModerator(Authentication a, @PathVariable Long id, @RequestBody ModeratorUpdate b){
    return admin.updateModerator(admin(a), id, b.email(), b.universityId(), b.active());
  }
  @PostMapping("/moderators/{id}/reset-credentials") public AdminService.ResetCredentialsResult resetModeratorCredentials(Authentication a, @PathVariable Long id, HttpServletRequest req){
    return admin.resetModeratorCredentials(admin(a), id, ip(req));
  }
  @PostMapping("/moderators") public AdminAccountDto createModerator(Authentication a, @RequestBody ModeratorBody b, HttpServletRequest req){
    return admin.createModerator(admin(a), b.email(), b.universityId(), b.password(), ip(req));
  }
  @PostMapping("/moderators/{id}/deactivate") public Map<String,String> deactivateModerator(Authentication a, @PathVariable Long id, @RequestBody ReasonBody b, HttpServletRequest req){
    admin.deactivateAdmin(admin(a), id, b.reason(), ip(req)); return Map.of("status","deactivated");
  }
  @GetMapping("/analytics") public Map<String,Object> analytics(Authentication a){ return admin.analytics(admin(a)); }
  @GetMapping("/audit-logs") public List<AuditLogDto> auditLogs(Authentication a, @RequestParam(defaultValue="50") int limit){
    return admin.auditLogs(admin(a), Math.min(limit, 200));
  }
  /** Audit export (FR-ADM-11): CSV of the same rows the list endpoint returns. */
  @GetMapping(value="/audit-logs/export", produces="text/csv")
  public ResponseEntity<String> auditExport(Authentication a, @RequestParam(defaultValue="200") int limit){
    List<AuditLogDto> rows = admin.auditLogs(admin(a), Math.min(limit, 1000));
    StringBuilder csv = new StringBuilder("id,createdAt,actorAdminId,action,targetType,targetId,reason,ip\n");
    for (AuditLogDto r : rows){
      csv.append(r.id()).append(",").append(r.createdAt()).append(",").append(r.actorAdminId()).append(",")
        .append(csvCell(r.action())).append(",").append(csvCell(r.targetType())).append(",")
        .append(r.targetId()==null?"":r.targetId()).append(",").append(csvCell(r.reason())).append(",")
        .append(csvCell(r.ip())).append("\n");
    }
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
      .body(csv.toString());
  }
  /** CSV cell: strip CR (row-break injection) and defuse formula injection —
   *  a cell starting with =, +, - or @ is prefixed with a single quote so a
   *  spreadsheet does not execute it as a formula. */
  private static String csvCell(String v){
    if (v==null) return "";
    String s = v.replace("\r","").replace("\"","\"\"");
    if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0))>=0) s = "'"+s;
    return (s.contains(",") || s.contains("\"") || s.contains("\n")) ? "\""+s+"\"" : s;
  }
  @GetMapping("/alerts") public List<AdminAlertDto> alerts(Authentication a){
    return admin.adminAlerts(admin(a));
  }
}
