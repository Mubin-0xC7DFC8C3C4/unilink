package com.unilink.jobs;
import com.unilink.admin.AdminService; import com.unilink.audit.AuditService;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.storage.StorageGateway; import com.unilink.team.TeamService;
import org.slf4j.*; import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.util.List;
/** Jobs (Master Guide §8): event archiving every minute, overdue alerts 15 min, purges + audit verify daily. */
@Component
public class ScheduledJobs {
  private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
  private final EventDetailRepository events; private final PostRepository posts; private final TeamService teams;
  private final AdminService adminService; private final AuditService audit; private final UserRepository users;
  private final VerificationRequestRepository requests; private final StorageGateway storage;
  public ScheduledJobs(EventDetailRepository events, PostRepository posts, TeamService teams, AdminService adminService,
      AuditService audit, UserRepository users, VerificationRequestRepository requests, StorageGateway storage){
    this.events=events; this.posts=posts; this.teams=teams; this.adminService=adminService; this.audit=audit;
    this.users=users; this.requests=requests; this.storage=storage;
  }
  /** Archive events whose deadline passed and close their teams (D-024). Idempotent. */
  @Scheduled(fixedRate = 60000) @Transactional
  public void archivePastEvents(){
    List<EventDetail> due = events.findByArchivedAtIsNullAndDeadlineBefore(LocalDateTime.now());
    for (EventDetail ev : due){
      ev.setArchivedAt(LocalDateTime.now()); events.save(ev);
      posts.findById(ev.getId()).ifPresent(p -> { p.setStatus("ARCHIVED"); posts.save(p); });
      teams.closeTeamsForEvent(ev.getId());
    }
    if (!due.isEmpty()) log.info("Archived {} past events", due.size());
  }
  @Scheduled(fixedRate = 15*60*1000) @Transactional
  public void overdueVerificationAlerts(){
    int n = adminService.createOverdueAlerts();
    if (n>0) log.info("Created {} overdue verification alerts", n);
  }
  /** Purge unconfirmed registrations older than 7 days with their images (D-015/D-052). */
  @Scheduled(cron = "0 0 3 * * *") @Transactional
  public void purgeUnconfirmed(){
    LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
    List<User> stale = users.findByState("REGISTERED").stream()
      .filter(u -> u.getCreatedAt()!=null && u.getCreatedAt().isBefore(cutoff)).toList();
    for (User u : stale){
      if (u.getIdCardKey()!=null) storage.delete(u.getIdCardKey());
      if (u.getProfilePhotoKey()!=null) storage.delete(u.getProfilePhotoKey());
      users.delete(u);
    }
    if (!stale.isEmpty()) log.info("Purged {} unconfirmed registrations", stale.size());
  }
  /** ID image retention (D-053): 7 days after approval, 30 days after rejection. */
  @Scheduled(cron = "0 30 3 * * *") @Transactional
  public void purgeIdImages(){
    for (VerificationRequest r : requests.findAll()){
      if (r.getDecidedAt()==null || r.getIdCardKey()==null) continue;
      int keep = "APPROVED".equals(r.getStatus()) ? 7 : 30;
      if (r.getDecidedAt().isBefore(LocalDateTime.now().minusDays(keep))){
        storage.delete(r.getIdCardKey()); r.setIdCardKey(null); requests.save(r);
      }
    }
  }
  @Scheduled(cron = "0 0 4 * * *")
  public void verifyAuditChain(){
    Long broken = audit.verifyChain();
    if (broken!=null) log.error("AUDIT CHAIN BROKEN at row {}", broken);
    else log.info("Audit hash chain verified OK");
  }
}
