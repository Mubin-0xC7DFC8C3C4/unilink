package com.unilink.admin.api;
import com.unilink.admin.AdminService; import com.unilink.admin.AdminDtos.*;
import com.unilink.identity.dto.CatalogDtos.*; import com.unilink.identity.service.VerificationFacade;
import com.unilink.moderation.ModerationDtos.*;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import com.unilink.storage.StorageGateway;
import jakarta.servlet.http.HttpServletRequest; import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*; import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** Admin Portal API (FR-ADM-01..14, FR-MOD-01..05). All endpoints require MFA_VERIFIED (filter chain).
 *  Thin controller (ARCH): HTTP translation only — all reads/writes go through services and return DTOs. */
@RestController @RequestMapping("/api/admin")
public class AdminController {
  private final AdminService admin; private final VerificationFacade verification;
  private final AccessPolicy policy; private final StorageGateway storage;
  public AdminController(AdminService admin, VerificationFacade verification, AccessPolicy policy, StorageGateway storage){
    this.admin=admin; this.verification=verification; this.policy=policy; this.storage=storage;
  }
  private Principals.AdminPrincipal admin(Authentication a){ return policy.requireAdmin(a.getPrincipal()); }
  private String ip(HttpServletRequest req){
    String fwd = req.getHeader("X-Forwarded-For");
    return fwd!=null ? fwd.split(",")[0].trim() : req.getRemoteAddr();
  }
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
  /** ID image: authorised stream only, never a public URL (D-053). */
  @GetMapping("/verification/{id}/id-image")
  public ResponseEntity<InputStreamResource> idImage(Authentication a, @PathVariable Long id){
    String key = admin.verificationIdCardKey(admin(a), id); // enforces moderator scope (404 out of scope)
    if (key==null || !storage.exists(key)) throw new com.unilink.common.Exceptions.NotFound("ID image");
    return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
      .header("Cache-Control","no-store")
      .body(new InputStreamResource(storage.open(key)));
  }
  @GetMapping("/users") public List<AdminUserDto> users(Authentication a, @RequestParam(required=false) String q){ return admin.listUsers(admin(a), q); }
  @GetMapping("/users/{id}") public AdminUserDto user(Authentication a, @PathVariable Long id){ return admin.getUser(admin(a), id); }
  @PostMapping("/users/{id}/{action}") public AdminUserDto userAction(Authentication a, @PathVariable Long id, @PathVariable String action, @RequestBody ReasonBody b, HttpServletRequest req){
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
    admin(a); return admin.pendingAppeals();
  }
  @PostMapping("/appeals/{id}/decide") public AppealDto decideAppeal(Authentication a, @PathVariable Long id, @RequestBody DecideBody b, HttpServletRequest req){
    return admin.decideAppeal(admin(a), id, "ACCEPT".equalsIgnoreCase(b.action()), b.reason(), ip(req));
  }
  @PostMapping("/announcements") public AnnouncementDto announce(Authentication a, @RequestBody AnnouncementBody b, HttpServletRequest req){
    return admin.createAnnouncement(admin(a), b.title(), b.body(), b.scope(), b.universityId(), b.expiresAt(), ip(req));
  }
  @PostMapping("/master-data/universities") public UniversityDto createUniversity(Authentication a, @RequestBody MasterBody b, HttpServletRequest req){
    return admin.createUniversity(admin(a), b.name(), b.shortName(), b.emailDomains(), ip(req));
  }
  @PostMapping("/master-data/departments") public DepartmentDto createDepartment(Authentication a, @RequestBody MasterBody b, HttpServletRequest req){
    return admin.createDepartment(admin(a), b.universityId(), b.catalogId(), b.name(), ip(req));
  }
  @PostMapping("/master-data/sectors") public SectorTagDto createSector(Authentication a, @RequestBody MasterBody b, HttpServletRequest req){
    return admin.createSector(admin(a), b.name(), ip(req));
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
  @GetMapping("/alerts") public List<AdminAlertDto> alerts(Authentication a){
    return admin.adminAlerts(admin(a));
  }
}
